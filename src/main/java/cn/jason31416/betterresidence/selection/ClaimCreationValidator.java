package cn.jason31416.betterresidence.selection;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClaimCreationValidator {
    private ClaimCreationValidator() {
    }

    public static ValidationResult validate(Player player, SelectionManager.Selection selection) {
        if (!selection.isComplete()) {
            return ValidationResult.invalid(Reason.INCOMPLETE_SELECTION, null, null, 0L, 0D);
        }
        if (!selection.isSameWorld()) {
            return ValidationResult.invalid(Reason.DIFFERENT_WORLDS, null, null, 0L, 0D);
        }

        AreaBox areaBox = selection.toAreaBox();
        World world = selection.getWorld();
        if (areaBox == null || world == null) {
            return ValidationResult.invalid(Reason.INCOMPLETE_SELECTION, null, null, 0L, 0D);
        }

        long size = areaBox.volume();
        double price = size * Math.max(0D, Config.getDouble("claim.create.price-per-block"));
        String worldUuid = world.getUID().toString();
        List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas = ClaimManager.fetchOverlappingClaimAreas(worldUuid, areaBox);
        if (!overlappingAreas.isEmpty()) {
            return validateSubclaim(player, areaBox, world, size, overlappingAreas);
        }

        int maxClaims = Config.getInt("claim.max-claims-per-player");
        if (maxClaims >= 0 && ClaimManager.fetchTopLevelClaimsByOwner(player.getUniqueId()).size() >= maxClaims) {
            return ValidationResult.invalid(Reason.MAX_CLAIMS, areaBox, world, size, price);
        }

        SimplePlayer simplePlayer = SimplePlayer.of(player);
        if (simplePlayer.getBalance() < price) {
            return ValidationResult.invalid(Reason.NOT_ENOUGH_MONEY, areaBox, world, size, price);
        }

        return ValidationResult.validTopLevel(areaBox, world, size, price);
    }

    private static ValidationResult validateSubclaim(Player player, AreaBox areaBox, World world, long size,
                                                     List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas) {
        String worldUuid = world.getUID().toString();
        Claim parent = findDeepestCoveringClaim(worldUuid, areaBox, overlappingAreas);
        if (parent == null) {
            return ValidationResult.invalid(Reason.PARTIAL_OVERLAP, areaBox, world, size, 0D, createConflictText(overlappingAreas));
        }

        Set<String> allowedOverlaps = ClaimManager.fetchAncestorOrSelfClaimUuids(parent.getUuid());
        boolean hasDisallowedOverlap = overlappingAreas.stream()
                .anyMatch(area -> !allowedOverlaps.contains(area.claimUuid()));
        if (hasDisallowedOverlap) {
            return ValidationResult.invalid(Reason.SUBCLAIM_OVERLAP, areaBox, world, size, 0D, createConflictText(overlappingAreas), parent);
        }

        if (!parent.checkPlayerPermission(SimplePlayer.of(player), "admin.subclaim", null)) {
            return ValidationResult.invalid(Reason.NO_PARENT_ADMIN, areaBox, world, size, 0D, "", parent);
        }

        int maxSubclaims = Config.getInt("claim.max-subclaims-per-claim");
        if (maxSubclaims >= 0 && parent.getSubClaims().size() >= maxSubclaims) {
            return ValidationResult.invalid(Reason.MAX_SUBCLAIMS, areaBox, world, size, 0D, "", parent);
        }

        int maxDepth = Config.getInt("claim.max-subclaim-depth");
        if (maxDepth >= 0 && parent.getDepth() + 1 > maxDepth) {
            return ValidationResult.invalid(Reason.MAX_SUBCLAIM_DEPTH, areaBox, world, size, 0D, "", parent);
        }

        return ValidationResult.validSubclaim(areaBox, world, size, parent);
    }

    @Nullable
    private static Claim findDeepestCoveringClaim(String worldUuid, AreaBox areaBox,
                                                  List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas) {
        Set<String> candidateUuids = new HashSet<>();
        for (ClaimManager.OverlappingClaimAreaInfo overlappingArea : overlappingAreas) {
            candidateUuids.add(overlappingArea.claimUuid());
        }

        return candidateUuids.stream()
                .map(ClaimManager::fetchClaim)
                .filter(claim -> claim != null)
                // A claim is a valid parent only if its full area union covers the selection. This
                // intentionally allows crossing adjacent areas in the same parent while rejecting
                // any tiny gap that would leave part of the subclaim outside the parent claim.
                .filter(claim -> ClaimManager.isAreaCoveredByClaim(claim.getUuid(), worldUuid, areaBox))
                .max(Comparator.comparingInt(Claim::getDepth))
                .orElse(null);
    }

    public enum Reason {
        NONE,
        INCOMPLETE_SELECTION,
        DIFFERENT_WORLDS,
        OVERLAP,
        MAX_CLAIMS,
        NOT_ENOUGH_MONEY,
        PARTIAL_OVERLAP,
        NO_PARENT_ADMIN,
        MAX_SUBCLAIMS,
        MAX_SUBCLAIM_DEPTH,
        SUBCLAIM_OVERLAP
    }

    public enum CreationType {
        TOP_LEVEL,
        SUBCLAIM
    }

    public enum VisualState {
        AVAILABLE,
        LIMITED,
        CONFLICT
    }

    private static String createConflictText(List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas) {
        List<String> claimNames = overlappingAreas.stream()
                .map(ClaimManager.OverlappingClaimAreaInfo::claimName)
                .distinct()
                .toList();
        if (claimNames.size() <= 1) {
            return claimNames.getFirst();
        }
        return claimNames.getFirst() + " +" + (claimNames.size() - 1);
    }

    public record ValidationResult(boolean valid, Reason reason, CreationType creationType, @Nullable AreaBox areaBox,
                                   @Nullable World world, long size, double price, String conflict,
                                   @Nullable Claim parentClaim) {
        private static ValidationResult validTopLevel(AreaBox areaBox, World world, long size, double price) {
            return new ValidationResult(true, Reason.NONE, CreationType.TOP_LEVEL, areaBox, world, size, price, "", null);
        }

        private static ValidationResult validSubclaim(AreaBox areaBox, World world, long size, Claim parentClaim) {
            return new ValidationResult(true, Reason.NONE, CreationType.SUBCLAIM, areaBox, world, size, 0D, "", parentClaim);
        }

        private static ValidationResult invalid(Reason reason, @Nullable AreaBox areaBox, @Nullable World world, long size, double price) {
            return invalid(reason, areaBox, world, size, price, "");
        }

        private static ValidationResult invalid(Reason reason, @Nullable AreaBox areaBox, @Nullable World world, long size, double price, String conflict) {
            return invalid(reason, areaBox, world, size, price, conflict, null);
        }

        private static ValidationResult invalid(Reason reason, @Nullable AreaBox areaBox, @Nullable World world,
                                                long size, double price, String conflict, @Nullable Claim parentClaim) {
            CreationType creationType = parentClaim == null ? CreationType.TOP_LEVEL : CreationType.SUBCLAIM;
            return new ValidationResult(false, reason, creationType, areaBox, world, size, price, conflict, parentClaim);
        }

        public VisualState visualState() {
            if (valid) {
                return VisualState.AVAILABLE;
            }
            if (reason == Reason.OVERLAP || reason == Reason.PARTIAL_OVERLAP || reason == Reason.SUBCLAIM_OVERLAP) {
                return VisualState.CONFLICT;
            }
            return VisualState.LIMITED;
        }
    }
}
