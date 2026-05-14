package cn.jason31416.betterresidence.claim;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.MapTree;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    private List<PermissionNode> permissionNodes = null;
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
        return createArea(world.getName(), areaBox);
    }

    /**
     * Create a block-aligned area for this claim.
     * @param worldName The world name the area belongs to.
     * @param areaBox Inclusive integer block bounds.
     * @return The created area id.
     */
    @SneakyThrows
    public int createArea(String worldName, AreaBox areaBox) {
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
                            .value("world", worldName)
            ));

            ClaimAreaLookup.clearCache();
            return areaId;
        }
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
                return Pair.of(Lang.messageLoader.getRawMessage("claim.group.owner", "Owner"), 1000);
            }

            // Query player_groups to find the group this player belongs to
            Optional<MapTree> playerGroup = DataHandler.getDatabase().select("player_groups")
                    .keyEquals("player_uuid", p.getUUID().toString())
                    .keyEquals("claim_uuid", uuid)
                    .one();

            if (playerGroup.isPresent()) {
                String groupId = playerGroup.get().getString("group_id");

                if(groupId.equals("trusted")){
                    return Pair.of(Lang.messageLoader.getRawMessage("claim.group.trusted", "Trusted"), 900);
                }

                // Query group_weights to get the weight for this group
                Optional<MapTree> groupWeight = DataHandler.getDatabase().select("group_weights")
                        .keyEquals("group_id", groupId)
                        .keyEquals("claim_uuid", uuid)
                        .one();

                if (groupWeight.isPresent()) {
                    return Pair.of(groupWeight.get().getString("group_name"), groupWeight.get().getInt("weight"));
                }
            }

            // Default weight for everyone (not trusted, not owner)
            return Pair.of(Lang.messageLoader.getRawMessage("claim.group.none", "None"), 0);
        });
    }

    public int getPlayerWeight(SimplePlayer player) {
        return getPlayerGroup(player).second();
    }

    /**
     * Load all permission nodes into memory
     */
    private void fetchPermissionNodes() {
        permissionNodes = new ArrayList<>();
        List<MapTree> rows = DataHandler.getDatabase().select("claim_permissions")
                .keyEquals("claim_uuid", uuid)
                .list();
        for (MapTree row : rows) {
            String permission = row.getString("permission");
            String name;
            String material = null;
            if (permission.contains(":")) {
                String[] parts = permission.split(":", 2);
                name = parts[0];
                material = parts[1];
            } else {
                name = permission;
            }
            int weight = row.getInt("weight");
            boolean state = row.getBoolean("value");
            permissionNodes.add(new PermissionNode(uuid, name, material, weight, state));
        }
    }

    /**
     * Check a weight's permission to do something
     * @param weight The weight of the player.
     * @param permission The action
     * @param material Nullable. The material of the action (Depending on the action, can be block, item, or entity depending on the event.)
     */
    private boolean checkWeightPermission(int weight, String permission, @Nullable String material){
        if(weight==1000) return true; // Owner always have all permissions.
        if(permissionNodes==null) fetchPermissionNodes();
        Optional<PermissionNode> result = permissionNodes.stream()
                .filter(node->weight>=node.getWeight())
                .filter(node->node.getName().equals(permission))
                .max(Comparator.comparingInt(node->{
                    if(material==null) return 0; // If material=null, we assume that theres only one permission
                    if(node.getMaterial().equals(material.toLowerCase(Locale.ROOT))){
                        return Integer.MAX_VALUE;
                    }else{
                        MaterialGroup group = MaterialGroup.getMaterialGroup(node.getMaterial());
                        if(group==null||!group.isInGroup(material)){
                            return -1;
                        }
                        return group.getPriority();
                    }
                }));
        return result.map(PermissionNode::isState).orElse(false);
    }

    /**
     * Check a player's permission
     * Too simple too lazy to make doc
     */
    public boolean checkPlayerPermission(SimplePlayer player, String permission, @Nullable String material){
        return checkWeightPermission(getPlayerWeight(player), permission, material);
    }
}
