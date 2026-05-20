package cn.jason31416.betterresidence.migration;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.core.FlagRegistry;
import cn.jason31416.betterresidence.core.PermissionRegistry;
import cn.jason31416.betterresidence.core.PermissionTargetType;
import cn.jason31416.betterresidence.core.TargetGroup;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.CuboidArea;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Best-effort importer for Zrips Residence. Keep Residence-specific code here so the rest of
 * BetterResidence stays independent from Residence's API and data model.
 */
public final class ResidenceMigration {
    private static final int VISITOR_WEIGHT = DefaultClaimGroupRegistry.VISITOR_WEIGHT;
    private static final int TRUSTED_WEIGHT = DefaultClaimGroupRegistry.getTrustedGroup().weight();
    private static final int ADMIN_WEIGHT = 900;
    private static final int OWNER_WEIGHT = DefaultClaimGroupRegistry.getOwnerGroup().weight();

    private static final Map<String, List<String>> PERMISSION_MAP = Map.ofEntries(
            Map.entry("build", List.of("block.break", "block.place")),
            Map.entry("destroy", List.of("block.break")),
            Map.entry("place", List.of("block.place")),
            Map.entry("use", List.of("block.interact")),
            Map.entry("container", List.of("block.interact:container")),
            Map.entry("door", List.of("block.interact:doors")),
            Map.entry("button", List.of("block.interact:redstone")),
            Map.entry("lever", List.of("block.interact:redstone")),
            Map.entry("pressure", List.of("block.interact:redstone")),
            Map.entry("redstone", List.of("block.interact:redstone")),
            Map.entry("table", List.of("block.interact:workstations")),
            Map.entry("brew", List.of("block.interact:workstations")),
            Map.entry("bed", List.of("block.interact:beds")),
            Map.entry("move", List.of("enter")),
            Map.entry("subzone", List.of("admin.subclaim")),
            Map.entry("admin", List.of("admin")),
            Map.entry("ignite", List.of("block.place:fire"))
    );

    private static final Map<String, String> BOOLEAN_FLAG_MAP = Map.ofEntries(
            Map.entry("piston", "piston"),
            Map.entry("tnt", "explosion.tnt"),
            Map.entry("creeper", "explosion.creeper"),
            Map.entry("fireball", "explosion.fireball"),
            Map.entry("witherdamage", "explosion.wither"),
            Map.entry("firespread", "fire.spread"),
            Map.entry("burn", "fire.burn"),
            Map.entry("monsters", "spawn.monster"),
            Map.entry("monster", "spawn.monster"),
            Map.entry("animals", "spawn.animal"),
            Map.entry("animal", "spawn.animal")
    );

    private ResidenceMigration() {
    }

    public static MigrationResult migrate(CommandSender sender, boolean dryRun) {
        MigrationState state = new MigrationState(dryRun);
        Plugin residencePlugin = Bukkit.getPluginManager().getPlugin("Residence");
        if (residencePlugin == null || !residencePlugin.isEnabled()) {
            state.warn("Residence is not installed or not enabled; nothing was imported.");
            return state.result();
        }

        Residence residence = Residence.getInstance();
        if (residence == null || residence.getResidenceManager() == null) {
            state.warn("Residence API is not ready; try again after Residence has fully loaded.");
            return state.result();
        }

        List<ClaimedResidence> topLevelResidences = residence.getResidenceManager().getFromAllResidences(true, false, null);
        sender.sendMessage("[BetterResidence] Found " + topLevelResidences.size() + " top-level Residence claims" + (dryRun ? " (dry run)." : "."));
        for (ClaimedResidence residenceClaim : topLevelResidences) {
            importResidence(residenceClaim, null, state);
        }
        ClaimManager.clearCache();
        return state.result();
    }

    private static void importResidence(ClaimedResidence residence, @Nullable String parentUuid, MigrationState state) {
        state.scanned++;
        String sourceName = residence.getName();
        String claimName = state.uniqueName(sourceName);
        if (ClaimManager.claimNameExists(claimName)) {
            state.skipped++;
            state.warn("Skipped " + sourceName + ": BetterResidence claim name already exists as " + claimName + ".");
            return;
        }

        List<ConvertedArea> areas = convertAreas(residence, state);
        if (areas.isEmpty()) {
            state.skipped++;
            state.warn("Skipped " + sourceName + ": no usable Residence physical areas were found.");
            return;
        }
        if (parentUuid != null && !areasCoveredByParent(parentUuid, areas)) {
            state.skipped++;
            state.warn("Skipped subzone " + sourceName + ": at least one area is not covered by its parent claim.");
            return;
        }
        if (parentUuid == null && overlapsExistingClaim(areas)) {
            state.skipped++;
            state.warn("Skipped " + sourceName + ": it overlaps an existing BetterResidence claim.");
            return;
        }

        UUID ownerUuid = residence.getPermissions().getOwnerUUID();
        if (ownerUuid == null) {
            state.skipped++;
            state.warn("Skipped " + sourceName + ": owner UUID is unavailable.");
            return;
        }

        Claim importedClaim = null;
        if (!state.dryRun) {
            ConvertedArea firstArea = areas.getFirst();
            importedClaim = ClaimManager.createClaim(SimplePlayer.of(ownerUuid), claimName, parentUuid, firstArea.worldUuid(), firstArea.box());
            for (int i = 1; i < areas.size(); i++) {
                ConvertedArea area = areas.get(i);
                importedClaim.createArea(area.worldUuid(), area.box());
            }
            importMessages(residence, importedClaim);
            importFlagsAndPermissions(residence, importedClaim, state);
            importPlayerGroups(residence, importedClaim, state);
        }

        state.created++;
        String importedUuid = importedClaim == null ? "dry-run:" + claimName : importedClaim.getUuid();
        state.imported.put(residence, importedUuid);
        state.usedNames.add(claimName.toLowerCase(Locale.ROOT));

        for (ClaimedResidence subzone : residence.getSubzones()) {
            importResidence(subzone, importedUuid.startsWith("dry-run:") ? null : importedUuid, state);
        }
    }

    private static List<ConvertedArea> convertAreas(ClaimedResidence residence, MigrationState state) {
        List<ConvertedArea> areas = new ArrayList<>();
        for (CuboidArea area : residence.getAreaArray()) {
            World world = area.getWorld();
            if (world == null) {
                world = Bukkit.getWorld(area.getWorldName());
            }
            if (world == null) {
                state.warn("Skipped area in " + residence.getName() + ": world is not loaded: " + area.getWorldName() + ".");
                continue;
            }

            Vector low = area.getLowVector();
            Vector high = area.getHighVector();
            areas.add(new ConvertedArea(
                    world.getUID().toString(),
                    new AreaBox(
                            low.getBlockX(),
                            high.getBlockX(),
                            low.getBlockY(),
                            high.getBlockY(),
                            low.getBlockZ(),
                            high.getBlockZ()
                    )
            ));
        }
        return areas;
    }

    private static boolean areasCoveredByParent(String parentUuid, List<ConvertedArea> areas) {
        for (ConvertedArea area : areas) {
            if (!ClaimManager.isAreaCoveredByClaim(parentUuid, area.worldUuid(), area.box())) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlapsExistingClaim(List<ConvertedArea> areas) {
        for (ConvertedArea area : areas) {
            if (!ClaimManager.fetchOverlappingClaimAreas(area.worldUuid(), area.box()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void importMessages(ClaimedResidence residence, Claim claim) {
        setFlagIfValid(claim, "enter-message", residence.getEnterMessage());
        setFlagIfValid(claim, "leave-message", residence.getLeaveMessage());
    }

    private static void importFlagsAndPermissions(ClaimedResidence residence, Claim claim, MigrationState state) {
        for (Map.Entry<String, Boolean> entry : residence.getPermissions().getFlags().entrySet()) {
            String residenceFlag = entry.getKey().toLowerCase(Locale.ROOT);
            boolean allowed = entry.getValue();

            if (residenceFlag.equals("flow")) {
                setFlagIfValid(claim, "flow", allowed ? "allow" : "deny");
                continue;
            }
            if (residenceFlag.equals("waterflow")) {
                setFlagIfValid(claim, "flow.water", allowed ? "allow" : "deny");
                continue;
            }
            if (residenceFlag.equals("lavaflow")) {
                setFlagIfValid(claim, "flow.lava", allowed ? "allow" : "deny");
                continue;
            }

            String betterFlag = BOOLEAN_FLAG_MAP.get(residenceFlag);
            if (betterFlag != null) {
                setFlagIfValid(claim, betterFlag, Boolean.toString(allowed));
                continue;
            }

            List<String> permissions = PERMISSION_MAP.get(residenceFlag);
            if (permissions != null) {
                int weight = allowed ? VISITOR_WEIGHT : TRUSTED_WEIGHT;
                for (String permission : permissions) {
                    setPermissionIfValid(claim, permission, weight, state);
                }
                continue;
            }

            state.unmappedFlags.add(residenceFlag);
        }
    }

    private static void importPlayerGroups(ClaimedResidence residence, Claim claim, MigrationState state) {
        for (Map.Entry<UUID, Map<String, Boolean>> playerEntry : residence.getPermissions().getPlayerFlags().entrySet()) {
            UUID playerUuid = playerEntry.getKey();
            Map<String, Boolean> flags = playerEntry.getValue();
            if (playerUuid == null || flags == null || flags.isEmpty() || playerUuid.equals(claim.getOwner().getUUID())) {
                continue;
            }

            String groupName = null;
            if (Boolean.TRUE.equals(flags.get("admin"))) {
                groupName = findConfiguredGroupName("admin");
            }
            if (groupName == null && Boolean.FALSE.equals(flags.get("move"))) {
                groupName = findConfiguredGroupName("blacklisted");
            }
            if (groupName == null && looksTrusted(flags)) {
                groupName = DefaultClaimGroupRegistry.getTrustedGroup().name();
            }
            if (groupName == null) {
                state.warn("Skipped custom player flags for " + playerUuid + " in " + residence.getName() + ": no close BetterResidence group mapping.");
                continue;
            }

            boolean changed = claim.setPlayerGroup(SimplePlayer.of(playerUuid), groupName);
            if (!changed) {
                state.warn("Could not assign " + playerUuid + " to group " + groupName + " in " + claim.getName() + ".");
            }
        }
    }

    private static boolean looksTrusted(Map<String, Boolean> flags) {
        return Boolean.TRUE.equals(flags.get("build"))
                || Boolean.TRUE.equals(flags.get("use"))
                || Boolean.TRUE.equals(flags.get("container"))
                || Boolean.TRUE.equals(flags.get("move"))
                || Boolean.TRUE.equals(flags.get("tp"));
    }

    @Nullable
    private static String findConfiguredGroupName(String groupId) {
        return DefaultClaimGroupRegistry.getConfiguredGroups().stream()
                .filter(group -> group.id().equals(groupId))
                .map(group -> group.name())
                .findFirst()
                .orElse(null);
    }

    private static void setFlagIfValid(Claim claim, String flag, @Nullable String value) {
        if (value == null || !FlagRegistry.isRegistered(flag)) {
            return;
        }
        claim.setFlag(flag, value);
    }

    private static void setPermissionIfValid(Claim claim, String permission, int weight, MigrationState state) {
        String basePermission = permission.split(":", 2)[0];
        if (!PermissionRegistry.isHierarchyPermission(basePermission)) {
            state.warn("Skipped permission mapping " + permission + ": BetterResidence permission is not registered.");
            return;
        }
        if (permission.contains(":")) {
            String target = permission.split(":", 2)[1];
            PermissionTargetType targetType = PermissionRegistry.getHierarchyTargetType(basePermission).orElse(PermissionTargetType.NONE);
            if (targetType.equals(PermissionTargetType.NONE)) {
                state.warn("Skipped permission mapping " + permission + ": permission does not support targets.");
                return;
            }
            if (TargetGroup.getTargetGroup(targetType, target) == null && target.matches("[a-z0-9_]+")) {
                // Literal Bukkit targets are also valid; the runtime matcher handles them.
            }
        }
        claim.setPermission(permission, weight);
    }

    private record ConvertedArea(String worldUuid, AreaBox box) {
    }

    private static final class MigrationState {
        private final boolean dryRun;
        private final Map<ClaimedResidence, String> imported = new IdentityHashMap<>();
        private final Set<String> usedNames = new HashSet<>();
        private final Set<String> unmappedFlags = new HashSet<>();
        private final List<String> warnings = new ArrayList<>();
        private int scanned;
        private int created;
        private int skipped;

        private MigrationState(boolean dryRun) {
            this.dryRun = dryRun;
        }

        private String uniqueName(String sourceName) {
            String baseName = sanitizeName(sourceName);
            String candidate = baseName;
            int suffix = 2;
            while (usedNames.contains(candidate.toLowerCase(Locale.ROOT)) || ClaimManager.claimNameExists(candidate)) {
                String suffixText = "_" + suffix++;
                int maxBaseLength = Math.max(1, 64 - suffixText.length());
                candidate = baseName.substring(0, Math.min(baseName.length(), maxBaseLength)) + suffixText;
            }
            return candidate;
        }

        private void warn(String warning) {
            warnings.add(warning);
        }

        private MigrationResult result() {
            if (!unmappedFlags.isEmpty()) {
                warnings.add("Unmapped Residence flags encountered: " + String.join(", ", unmappedFlags.stream().sorted().toList()) + ".");
            }
            return new MigrationResult(scanned, created, skipped, dryRun, List.copyOf(warnings));
        }
    }

    private static String sanitizeName(String sourceName) {
        String sanitized = sourceName == null ? "residence" : sourceName;
        sanitized = sanitized.replace('.', '_').replaceAll("[^A-Za-z0-9_-]", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (sanitized.isBlank()) {
            sanitized = "residence";
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 64));
    }

    public record MigrationResult(int scanned, int created, int skipped, boolean dryRun, List<String> warnings) {
        public String formatSummary() {
            return "[BetterResidence] Residence migration " + (dryRun ? "dry run" : "completed")
                    + ": scanned=" + scanned
                    + ", imported=" + created
                    + ", skipped=" + skipped
                    + ", warnings=" + warnings.size() + ".";
        }
    }
}
