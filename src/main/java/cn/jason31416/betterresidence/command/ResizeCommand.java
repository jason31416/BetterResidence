package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.visual.ClaimVisualDisplayManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ResizeCommand extends ChildCommand {
    private static final long RESIZE_FLASH_DURATION_MILLIS = 2_000L;

    private final boolean expand;

    public ResizeCommand(IParentCommand parent, String name, boolean expand) {
        super(name, parent);
        this.expand = expand;
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (context.args().size() != 1) {
            return Lang.getMessage(usageKey());
        }

        int blocks;
        try {
            blocks = Integer.parseInt(context.getArg(0));
        } catch (NumberFormatException ignored) {
            return Lang.getMessage("command.resize-invalid-blocks");
        }
        if (blocks <= 0) {
            return Lang.getMessage("command.resize-invalid-blocks");
        }

        AreaAtPlayer areaAtPlayer = findAreaAtPlayer(context);
        if (areaAtPlayer.error() != null) {
            return areaAtPlayer.error();
        }

        Player player = context.player().getPlayer();
        BlockFace face = getResizeFace(player.getLocation());
        AreaBox resizedBox = resize(areaAtPlayer.area().box(), face, blocks);
        if (resizedBox == null) {
            return Lang.getMessage("command.resize-too-small");
        }

        ResizeValidationResult result = validateResize(areaAtPlayer.claim(), areaAtPlayer.area(), resizedBox);
        if (!result.valid()) {
            return createValidationError(result);
        }

        SimplePlayer simplePlayer = SimplePlayer.of(player);
        if (result.price() > 0D && (simplePlayer.getBalance() < result.price() || !simplePlayer.withdrawBalance(result.price()))) {
            return Lang.getMessage("command.resize-not-enough-money").copy()
                    .add("price", formatPrice(result.price()))
                    .add("blocks", result.changedBlocks());
        }

        areaAtPlayer.claim().updateArea(areaAtPlayer.area().areaId(), resizedBox);
        ClaimVisualDisplayManager.flashClaim(player, new ClaimManager.ClaimAreaInfo(
                areaAtPlayer.area().areaId(),
                areaAtPlayer.area().worldUuid(),
                resizedBox
        ), RESIZE_FLASH_DURATION_MILLIS);
        return Lang.getMessage(expand ? "command.expand-success" : "command.contract-success").copy()
                .add("claim", ClaimCommandFormat.escape(areaAtPlayer.claim().getName()))
                .add("direction", face.name().toLowerCase(Locale.ROOT))
                .add("blocks", blocks)
                .add("changed-blocks", result.changedBlocks())
                .add("price", formatPrice(result.price()));
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            String prefix = context.getArg(0);
            return List.of("1", "5", "10", "16").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private AreaAtPlayer findAreaAtPlayer(ICommandContext context) {
        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return AreaAtPlayer.error(Lang.getMessage("command.not-in-claim"));
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.area.resize", null)) {
            return AreaAtPlayer.error(Lang.getMessage("command.no-claim-admin"));
        }

        SimpleLocation blockLocation = context.player().getLocation().getBlockLocation();
        String worldUuid = context.player().getLocation().getWorld().getBukkitWorld().getUID().toString();
        ClaimManager.ClaimAreaInfo area = ClaimManager.findClaimAreaAt(
                claim.getUuid(),
                worldUuid,
                (int) blockLocation.x(),
                (int) blockLocation.y(),
                (int) blockLocation.z()
        ).orElse(null);
        if (area == null) {
            return AreaAtPlayer.error(Lang.getMessage("command.resize-no-area"));
        }
        return new AreaAtPlayer(claim, area, null);
    }

    private BlockFace getResizeFace(Location location) {
        if (location.getPitch() <= -60F) {
            return BlockFace.UP;
        }
        if (location.getPitch() >= 60F) {
            return BlockFace.DOWN;
        }
        return yawToFace(location.getYaw());
    }

    private BlockFace yawToFace(float yaw) {
        float normalizedYaw = (yaw % 360F + 360F) % 360F;
        if (normalizedYaw >= 315F || normalizedYaw < 45F) {
            return BlockFace.SOUTH;
        }
        if (normalizedYaw < 135F) {
            return BlockFace.WEST;
        }
        if (normalizedYaw < 225F) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    @Nullable
    private AreaBox resize(AreaBox box, BlockFace face, int blocks) {
        int delta = expand ? blocks : -blocks;
        int minX = box.minX();
        int maxX = box.maxX();
        int minY = box.minY();
        int maxY = box.maxY();
        int minZ = box.minZ();
        int maxZ = box.maxZ();

        switch (face) {
            case UP -> maxY += delta;
            case DOWN -> minY -= delta;
            case NORTH -> minZ -= delta;
            case SOUTH -> maxZ += delta;
            case EAST -> maxX += delta;
            case WEST -> minX -= delta;
            default -> throw new IllegalStateException("Unsupported resize direction: " + face);
        }

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new AreaBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private ResizeValidationResult validateResize(Claim claim, ClaimManager.ClaimAreaInfo resizedArea, AreaBox resizedBox) {
        long oldSize = resizedArea.box().volume();
        long newSize = resizedBox.volume();
        long changedBlocks = Math.abs(newSize - oldSize);
        double price = claim.getParentUuid() == null && expand
                ? changedBlocks * Math.max(0D, Config.getDouble("claim.create.price-per-block"))
                : 0D;
        String worldUuid = resizedArea.worldUuid();

        if (claim.getParentUuid() != null && !ClaimManager.isAreaCoveredByClaim(claim.getParentUuid(), worldUuid, resizedBox)) {
            return ResizeValidationResult.invalid(ResizeValidationReason.OUTSIDE_PARENT, changedBlocks, price);
        }

        List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas = ClaimManager.fetchOverlappingClaimAreas(worldUuid, resizedBox).stream()
                .filter(area -> !(area.areaId() == resizedArea.areaId() && area.claimUuid().equals(claim.getUuid())))
                .toList();
        Set<String> allowedOverlapUuids = new HashSet<>(ClaimManager.fetchAncestorClaimUuids(claim.getUuid()));
        allowedOverlapUuids.addAll(ClaimManager.fetchDescendantClaimUuids(claim.getUuid()));
        boolean hasDisallowedOverlap = overlappingAreas.stream()
                .anyMatch(area -> !allowedOverlapUuids.contains(area.claimUuid()));
        if (hasDisallowedOverlap) {
            return ResizeValidationResult.invalid(ResizeValidationReason.OVERLAP, changedBlocks, price, createConflictText(overlappingAreas));
        }

        if (!descendantAreasRemainCovered(claim, resizedArea, resizedBox)) {
            return ResizeValidationResult.invalid(ResizeValidationReason.DESCENDANT_OUTSIDE, changedBlocks, price);
        }
        return ResizeValidationResult.valid(changedBlocks, price);
    }

    private boolean descendantAreasRemainCovered(Claim claim, ClaimManager.ClaimAreaInfo resizedArea, AreaBox resizedBox) {
        List<String> descendantUuids = ClaimManager.fetchDescendantClaimUuids(claim.getUuid());
        if (descendantUuids.isEmpty()) {
            return true;
        }

        List<ClaimManager.ClaimAreaInfo> updatedClaimAreas = ClaimManager.fetchClaimAreas(claim.getUuid()).stream()
                .map(area -> area.areaId() == resizedArea.areaId()
                        ? new ClaimManager.ClaimAreaInfo(area.areaId(), area.worldUuid(), resizedBox)
                        : area)
                .toList();
        for (String descendantUuid : descendantUuids) {
            for (ClaimManager.ClaimAreaInfo descendantArea : ClaimManager.fetchClaimAreas(descendantUuid)) {
                if (!isAreaCoveredByAreas(updatedClaimAreas, descendantArea.worldUuid(), descendantArea.box())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isAreaCoveredByAreas(List<ClaimManager.ClaimAreaInfo> coveringAreas, String worldUuid, AreaBox areaBox) {
        List<AreaBox> remaining = new ArrayList<>(List.of(areaBox));
        for (ClaimManager.ClaimAreaInfo coveringArea : coveringAreas) {
            if (!coveringArea.worldUuid().equals(worldUuid) || !coveringArea.box().overlaps(areaBox)) {
                continue;
            }
            List<AreaBox> nextRemaining = new ArrayList<>();
            for (AreaBox remainingBox : remaining) {
                nextRemaining.addAll(remainingBox.subtract(coveringArea.box()));
            }
            remaining = nextRemaining;
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return remaining.isEmpty();
    }

    private Message createValidationError(ResizeValidationResult result) {
        String key = switch (result.reason()) {
            case OUTSIDE_PARENT -> "command.resize-outside-parent";
            case OVERLAP -> "command.resize-overlap";
            case DESCENDANT_OUTSIDE -> "command.resize-descendant-outside";
            default -> "command.resize-unavailable";
        };
        return Lang.getMessage(key).copy()
                .add("price", formatPrice(result.price()))
                .add("blocks", result.changedBlocks())
                .add("conflict", result.conflict());
    }

    private String createConflictText(List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas) {
        List<String> claimNames = overlappingAreas.stream()
                .map(ClaimManager.OverlappingClaimAreaInfo::claimName)
                .distinct()
                .toList();
        if (claimNames.isEmpty()) {
            return "";
        }
        if (claimNames.size() <= 1) {
            return claimNames.getFirst();
        }
        return claimNames.getFirst() + " +" + (claimNames.size() - 1);
    }

    private String usageKey() {
        return expand ? "command.expand-usage" : "command.contract-usage";
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return Long.toString((long) price);
        }
        return String.format(Locale.ROOT, "%.2f", price);
    }

    private enum ResizeValidationReason {
        NONE,
        OUTSIDE_PARENT,
        OVERLAP,
        DESCENDANT_OUTSIDE
    }

    private record ResizeValidationResult(boolean valid, ResizeValidationReason reason, long changedBlocks,
                                          double price, String conflict) {
        private static ResizeValidationResult valid(long changedBlocks, double price) {
            return new ResizeValidationResult(true, ResizeValidationReason.NONE, changedBlocks, price, "");
        }

        private static ResizeValidationResult invalid(ResizeValidationReason reason, long changedBlocks, double price) {
            return invalid(reason, changedBlocks, price, "");
        }

        private static ResizeValidationResult invalid(ResizeValidationReason reason, long changedBlocks,
                                                      double price, String conflict) {
            return new ResizeValidationResult(false, reason, changedBlocks, price, conflict);
        }
    }

    private record AreaAtPlayer(@Nullable Claim claim, @Nullable ClaimManager.ClaimAreaInfo area, @Nullable Message error) {
        private static AreaAtPlayer error(Message error) {
            return new AreaAtPlayer(null, null, error);
        }
    }
}
