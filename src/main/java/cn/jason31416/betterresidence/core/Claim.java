package cn.jason31416.betterresidence.core;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.MapTree;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Claim {
    // Static method/variables
    protected static final Object areaCreateLock = new Object();

    @SneakyThrows
    protected static int allocateNextAreaId() {
        return DataHandler.getDatabase().getSqlInstance().executeQueryOne(
                "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM area",
                List.of(),
                rs -> rs.getInt("next_id")
        ).orElseThrow(() -> new IllegalStateException("Failed to allocate claim area id"));
    }

    // Basic attributes (Directly fetched from the table, stay in memory)
    @Getter
    private final SimplePlayer owner;
    @Getter
    private final String name;
    @Getter
    private final String uuid;
    @Getter
    @Nullable
    private final String parentUuid;

    private final Map<SimplePlayer, Pair<String, Integer>> playerGroupCache = new ConcurrentHashMap<>();

    protected List<PermissionNode> permissionNodes = null;
    private Map<String, String> claimFlags = null;
    protected List<String> subClaims = null;

    public Claim(SimplePlayer owner, String name, String uuid, @Nullable String parentUuid) {
        this.owner = owner;
        this.name = name;
        this.uuid = uuid;
        this.parentUuid = parentUuid;
    }

    public int getDepth() {
        int depth = 0;
        Set<String> seenClaims = new HashSet<>();
        Claim current = this;

        while (current.parentUuid != null && seenClaims.add(current.uuid)) {
            current = ClaimManager.fetchClaim(current.parentUuid);
            if (current == null) break;
            depth++;
        }

        return depth;
    }

    /**
     * Create a block-aligned area for this claim.
     * @param world The world the area belongs to.
     * @param areaBox Inclusive integer block bounds.
     * @return The created area id.
     */
    @SneakyThrows
    public int createArea(SimpleWorld world, AreaBox areaBox) {
        return createArea(world.getBukkitWorld().getUID().toString(), areaBox);
    }

    /**
     * Create a block-aligned area for this claim.
     * @param worldUuid The world UUID the area belongs to.
     * @param areaBox Inclusive integer block bounds.
     * @return The created area id.
     */
    @SneakyThrows
    public int createArea(String worldUuid, AreaBox areaBox) {
        synchronized (areaCreateLock) {
            int areaId = allocateNextAreaId();

            DataHandler.getDatabase().executeBatch(List.of(
                    DataHandler.getDatabase().insert("area")
                            .value("id", areaId)
                            .value("minX", areaBox.minX())
                            .value("maxX", areaBox.maxX())
                            .value("minY", areaBox.minY())
                            .value("maxY", areaBox.maxY())
                            .value("minZ", areaBox.minZ())
                            .value("maxZ", areaBox.maxZ()),
                    DataHandler.getDatabase().insert("claim_areas")
                            .value("area_id", areaId)
                            .value("claim_uuid", uuid)
                            .value("world", worldUuid)
            ));

            ClaimAreaLookup.clearCache();
            return areaId;
        }
    }

    @SneakyThrows
    public void removeArea(int areaId) {
        DataHandler.getDatabase().executeBatch(List.of(
                DataHandler.getDatabase().delete("claim_areas")
                        .keyEquals("area_id", areaId)
                        .keyEquals("claim_uuid", uuid),
                DataHandler.getDatabase().delete("area")
                        .keyEquals("id", areaId)
        ));
        ClaimAreaLookup.clearCache();
    }

    @SneakyThrows
    public void updateArea(int areaId, AreaBox areaBox) {
        DataHandler.getDatabase().update("area")
                .value("minX", areaBox.minX())
                .value("maxX", areaBox.maxX())
                .value("minY", areaBox.minY())
                .value("maxY", areaBox.maxY())
                .value("minZ", areaBox.minZ())
                .value("maxZ", areaBox.maxZ())
                .keyEquals("id", areaId)
                .executeUpdate();
        ClaimAreaLookup.clearCache();
    }

    // --------- Subclaim ---------------

    private void fetchSubclaims(){
        subClaims = new ArrayList<>();
        List<MapTree> rows = DataHandler.getDatabase().select("claim")
                .keyEquals("parent_uuid", uuid)
                .list();
        for (MapTree row : rows) {
            subClaims.add(row.getString("uuid"));
        }
    }

    public List<String> getSubClaims() {
        if (subClaims == null) fetchSubclaims();
        return subClaims;
    }

    /**
     * Remove this claim and all data directly owned by it. Direct subclaims are promoted to this claim's parent.
     */
    @SneakyThrows
    public void remove() {
        List<Integer> areaIds = ClaimManager.fetchClaimAreas(uuid).stream()
                .map(ClaimManager.ClaimAreaInfo::areaId)
                .toList();
        List<String> promotedSubclaims = new ArrayList<>(getSubClaims());

        DataHandler.getDatabase().update("claim")
                .value("parent_uuid", parentUuid)
                .keyEquals("parent_uuid", uuid)
                .executeUpdate();
        DataHandler.getDatabase().delete("claim_permissions")
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();
        DataHandler.getDatabase().delete("group_weights")
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();
        DataHandler.getDatabase().delete("player_groups")
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();
        DataHandler.getDatabase().delete("claim_flags")
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();
        DataHandler.getDatabase().delete("claim_areas")
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();
        for (int areaId : areaIds) {
            DataHandler.getDatabase().delete("area")
                    .keyEquals("id", areaId)
                    .executeUpdate();
        }
        DataHandler.getDatabase().delete("claim")
                .keyEquals("uuid", uuid)
                .executeUpdate();

        ClaimManager.invalidateClaim(uuid);
        ClaimManager.invalidateClaims(promotedSubclaims);
        if (parentUuid != null) {
            ClaimManager.invalidateClaim(parentUuid);
        }
        ClaimAreaLookup.clearCache();

        playerGroupCache.clear();
        permissionNodes = null;
        claimFlags = null;
        subClaims = null;
    }

    // --------- Flag system ------------

    /**
     * Load all claim settings into memory
     */
    private void fetchClaimFlags() {
        claimFlags = new HashMap<>();
        List<MapTree> rows = DataHandler.getDatabase().select("claim_flags")
                .keyEquals("claim_uuid", uuid)
                .list();
        for (MapTree row : rows) {
            claimFlags.put(row.getString("flag"), row.getString("value"));
        }
    }

    public String getStringFlag(String flag, String defaultValue) {
        if(claimFlags == null) fetchClaimFlags();
        return claimFlags.getOrDefault(flag, defaultValue);
    }

    public String getFlag(FlagRegistry.RegisteredFlag flag) {
        if(claimFlags == null) fetchClaimFlags();
        for (FlagRegistry.RegisteredFlag currentFlag : FlagRegistry.getFlagAndParents(flag)) {
            String value = claimFlags.get(currentFlag.id());
            if (value != null) {
                return value;
            }
        }
        return flag.defaultValue();
    }

    public Map<String, String> getStoredFlags() {
        if (claimFlags == null) fetchClaimFlags();
        return Map.copyOf(claimFlags);
    }

    public boolean setFlag(String flag, String value) {
        Optional<FlagRegistry.RegisteredFlag> registeredFlag = FlagRegistry.getFlag(flag);
        if (registeredFlag.isEmpty() || !registeredFlag.get().isValidValue(value)) {
            return false;
        }

        DataHandler.getDatabase().delete("claim_flags")
                .keyEquals("claim_uuid", uuid)
                .keyEquals("flag", flag)
                .executeUpdate();
        if (!value.equals(registeredFlag.get().defaultValue())) {
            DataHandler.getDatabase().insert("claim_flags")
                    .value("flag", flag)
                    .value("value", value)
                    .value("claim_uuid", uuid)
                    .executeUpdate();
        }
        claimFlags = null;
        return true;
    }

    public int getIntFlag(String flag, int defaultValue) {
        try {
            return Integer.parseInt(getStringFlag(flag, ""+defaultValue));
        }catch (Exception e) {
            return defaultValue;
        }
    }

    public double getDoubleFlag(String flag, double defaultValue) {
        try {
            return Double.parseDouble(getStringFlag(flag, ""+defaultValue));
        }catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBooleanFlag(String flag, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(getStringFlag(flag, ""+defaultValue));
        }catch (Exception e) {
            return defaultValue;
        }
    }

    // ----------- Permission system --------------

    /**
     * Find the player's group and weight
     * @param player The querying player
     * @return The player's group name + weight
     */
    public Pair<String, Integer> getPlayerGroup(SimplePlayer player) {
        return playerGroupCache.computeIfAbsent(player, p -> {
            // Owner always gets weight 1000
            if (p.equals(owner)) {
                ClaimGroup ownerGroup = DefaultClaimGroupRegistry.getOwnerGroup();
                return Pair.of(ownerGroup.name(), ownerGroup.weight());
            }

            // Query player_groups to find the group this player belongs to
            Optional<MapTree> playerGroup = DataHandler.getDatabase().select("player_groups")
                    .keyEquals("player_uuid", p.getUUID().toString())
                    .keyEquals("claim_uuid", uuid)
                    .one();

            if (playerGroup.isPresent()) {
                String groupId = playerGroup.get().getString("group_id");
                return getClaimGroupById(groupId)
                        .map(group -> Pair.of(group.name(), group.weight()))
                        .orElseGet(() -> Pair.of(DefaultClaimGroupRegistry.getVisitorName(), DefaultClaimGroupRegistry.VISITOR_WEIGHT));
            }

            // Default weight for everyone (not trusted, not owner)
            return Pair.of(DefaultClaimGroupRegistry.getVisitorName(), DefaultClaimGroupRegistry.VISITOR_WEIGHT);
        });
    }

    public int getPlayerWeight(SimplePlayer player) {
        return getPlayerGroup(player).second();
    }

    public boolean setPlayerGroup(SimplePlayer player, @Nullable String groupName) {
        if(player.equals(owner)) throw new IllegalArgumentException("Cannot set player group for owner");

        String groupId = null;
        if (groupName != null) {
            if (groupName.equals(DefaultClaimGroupRegistry.getVisitorName())) {
                groupName = null;
            } else if (groupName.equals(DefaultClaimGroupRegistry.getEveryoneName())) {
                return false;
            } else {
                Optional<ClaimGroup> group = getClaimGroupByName(groupName);
                if (group.isEmpty() || group.get().id().equals(DefaultClaimGroupRegistry.OWNER_ID)) {
                    return false;
                }
                groupId = group.get().id();
            }
        }

        DataHandler.getDatabase().delete("player_groups")
                .keyEquals("player_uuid", player.getUUID().toString())
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();

        if (groupName != null) {
            DataHandler.getDatabase().insert("player_groups")
                    .value("player_uuid", player.getUUID().toString())
                    .value("group_id", groupId)
                    .value("claim_uuid", uuid)
                    .executeUpdate();
        }

        playerGroupCache.remove(player);
        return true;
    }

    public Optional<Integer> getGroupWeight(String groupName) {
        if (groupName.equals(DefaultClaimGroupRegistry.getEveryoneName())) {
            return Optional.of(DefaultClaimGroupRegistry.EVERYONE_WEIGHT);
        }
        if (groupName.equals(DefaultClaimGroupRegistry.getVisitorName())) {
            return Optional.of(DefaultClaimGroupRegistry.VISITOR_WEIGHT);
        }

        return getClaimGroupByName(groupName).map(ClaimGroup::weight);
    }

    /**
     * Return all real permission groups available in this claim.
     * <p>
     * The list intentionally excludes visitor and everyone because they are not real assignable
     * groups. Callers can add visitor or everyone explicitly when their command accepts those
     * special names.
     */
    public List<ClaimGroup> getClaimGroups() {
        List<ClaimGroup> claimGroups = new ArrayList<>(DefaultClaimGroupRegistry.getConfiguredGroups());
        DataHandler.getDatabase().select("group_weights")
                .keyEquals("claim_uuid", uuid)
                .list()
                .forEach(row -> claimGroups.add(new ClaimGroup(
                        row.getString("group_id"),
                        row.getString("group_name"),
                        row.getInt("weight")
                )));
        return claimGroups;
    }

    public Optional<ClaimGroup> getClaimGroupById(String groupId) {
        return getClaimGroups().stream()
                .filter(group -> group.id().equals(groupId))
                .findFirst();
    }

    private Optional<ClaimGroup> getClaimGroupByName(String groupName) {
        return getClaimGroups().stream()
                .filter(group -> group.name().equals(groupName))
                .findFirst();
    }

    public void setPermission(String permission, int weight) {
        DataHandler.getDatabase().delete("claim_permissions")
                .keyEquals("permission", permission)
                .keyEquals("claim_uuid", uuid)
                .executeUpdate();

        DataHandler.getDatabase().insert("claim_permissions")
                .value("permission", permission)
                .value("weight", weight)
                .value("claim_uuid", uuid)
                .executeUpdate();

        permissionNodes = null;
    }

    /**
     * Load all permission nodes into memory
     */
    private void fetchPermissionNodes() {
        permissionNodes = new ArrayList<>();
        Set<String> storedPermissionKeys = new HashSet<>();
        List<MapTree> rows = DataHandler.getDatabase().select("claim_permissions")
                .keyEquals("claim_uuid", uuid)
                .list();
        for (MapTree row : rows) {
            String permission = row.getString("permission");
            storedPermissionKeys.add(permission);
            int weight = row.getInt("weight");
            permissionNodes.add(createPermissionNode(permission, weight));
        }

        if (!Config.contains("claim.default-permissions")) {
            return;
        }

        MapTree defaultPermissions = Config.getSection("claim.default-permissions");
        for (String key : defaultPermissions.getKeys()) {
            String permission = defaultPermissions.getString(key + ".permission");
            if (storedPermissionKeys.contains(permission)) {
                continue;
            }

            int weight = defaultPermissions.getInt(key + ".weight");
            permissionNodes.add(createPermissionNode(permission, weight));
        }
    }

    private PermissionNode createPermissionNode(String permission, int weight) {
        String name;
        String target = null;
        if (permission.contains(":")) {
            String[] parts = permission.split(":", 2);
            name = parts[0];
            target = parts[1];
        } else {
            name = permission;
        }
        return new PermissionNode(uuid, name, target, weight);
    }

    /**
     * Check a weight's permission to do something
     * @param weight The weight of the player.
     * @param permission The action
     * @param target Nullable. The target of the action, such as block material, item material, or entity type.
     */
    private boolean checkWeightPermission(int weight, String permission, @Nullable String target){
        if(weight==1000) return true; // Owner always have all permissions.
        if(permissionNodes==null) fetchPermissionNodes();
        PermissionTargetType targetType = PermissionRegistry.getPermission(permission)
                .map(PermissionRegistry.RegisteredPermission::targetType)
                .orElse(PermissionTargetType.NONE);
        Optional<PermissionNode> result = permissionNodes.stream()
                .filter(node->node.getPermissionPriority(permission)>=0)
                .filter(node->node.getTargetPriority(targetType, target)>=0)
                // Permission-name specificity is compared before material specificity.
                // Example: block.break:all must beat block:grass_block.
                .max(Comparator
                        .comparingInt((PermissionNode node) -> node.getPermissionPriority(permission))
                        .thenComparingInt(node -> node.getTargetPriority(targetType, target)));
        return result.map(node -> weight >= node.getWeight()).orElse(false);
    }

    /**
     * Check a player's permission
     * Too simple too lazy to make doc
     */
    public boolean checkPlayerPermission(SimplePlayer player, String permission, @Nullable String target){
        if (AdminModeManager.isAdminMode(player)) {
            return true;
        }
        return checkWeightPermission(getPlayerWeight(player), permission, target);
    }
}
