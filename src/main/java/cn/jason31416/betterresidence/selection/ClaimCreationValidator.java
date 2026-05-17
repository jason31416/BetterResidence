package cn.jason31416.betterresidence.selection;

import cn.jason31416.betterresidence.claim.AreaBox;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.List;

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
            return ValidationResult.invalid(Reason.OVERLAP, areaBox, world, size, price, createConflictText(overlappingAreas));
        }

        int maxClaims = Config.getInt("claim.max-claims-per-player");
        if (maxClaims >= 0 && ClaimManager.fetchClaimsByOwner(player.getUniqueId()).size() >= maxClaims) {
            return ValidationResult.invalid(Reason.MAX_CLAIMS, areaBox, world, size, price);
        }

        SimplePlayer simplePlayer = SimplePlayer.of(player);
        if (simplePlayer.getBalance() < price) {
            return ValidationResult.invalid(Reason.NOT_ENOUGH_MONEY, areaBox, world, size, price);
        }

        return ValidationResult.valid(areaBox, world, size, price);
    }

    public enum Reason {
        NONE,
        INCOMPLETE_SELECTION,
        DIFFERENT_WORLDS,
        OVERLAP,
        MAX_CLAIMS,
        NOT_ENOUGH_MONEY
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

    public record ValidationResult(boolean valid, Reason reason, @Nullable AreaBox areaBox, @Nullable World world, long size, double price, String conflict) {
        private static ValidationResult valid(AreaBox areaBox, World world, long size, double price) {
            return new ValidationResult(true, Reason.NONE, areaBox, world, size, price, "");
        }

        private static ValidationResult invalid(Reason reason, @Nullable AreaBox areaBox, @Nullable World world, long size, double price) {
            return invalid(reason, areaBox, world, size, price, "");
        }

        private static ValidationResult invalid(Reason reason, @Nullable AreaBox areaBox, @Nullable World world, long size, double price, String conflict) {
            return new ValidationResult(false, reason, areaBox, world, size, price, conflict);
        }

        public VisualState visualState() {
            if (valid) {
                return VisualState.AVAILABLE;
            }
            if (reason == Reason.OVERLAP) {
                return VisualState.CONFLICT;
            }
            return VisualState.LIMITED;
        }
    }
}
