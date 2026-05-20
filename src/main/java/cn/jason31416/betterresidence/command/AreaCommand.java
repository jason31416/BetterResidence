package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.selection.SelectionManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AreaCommand extends ChildCommand {
    private static final long CONFIRM_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final Map<UUID, PendingAreaRemoval> PENDING_REMOVALS = new HashMap<>();

    public AreaCommand(IParentCommand parent) {
        super("area", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.args().isEmpty()) {
            return Lang.getMessage("command.area-usage");
        }

        return switch (context.getArg(0).toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> addArea(context);
            case "remove" -> removeArea(context);
            case "list" -> listAreas(context);
            default -> Lang.getMessage("command.area-usage");
        };
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            return List.of("add", "remove", "list").stream()
                    .filter(command -> command.startsWith(context.getArg(0).toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private Message addArea(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (context.args().size() > 2) {
            return Lang.getMessage("command.area-add-usage");
        }

        Claim claim = context.args().size() == 2
                ? ClaimManager.resolveClaim(context.getArg(1))
                : ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            String claimInput = context.args().size() == 2 ? context.getArg(1) : "";
            return context.args().size() == 2
                    ? Lang.getMessage("command.claim-not-found").copy().add("claim", ClaimCommandFormat.escape(claimInput))
                    : Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.area.add", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        Player player = context.player().getPlayer();
        SelectionManager.Selection selection = SelectionManager.getSelection(player);
        AreaValidationResult result = validateSelectedArea(claim, player, selection);
        if (!result.valid()) {
            return createAreaAddValidationError(result);
        }

        SimplePlayer owner = SimplePlayer.of(player);
        if (result.price() > 0D && (owner.getBalance() < result.price() || !owner.withdrawBalance(result.price()))) {
            return Lang.getMessage("command.area-add-not-enough-money").copy()
                    .add("price", formatPrice(result.price()))
                    .add("size", result.size());
        }

        claim.createArea(result.world().getUID().toString(), result.areaBox());
        SelectionManager.clearSelection(player);
        String successKey = claim.getParentUuid() == null ? "command.area-add-success" : "command.area-add-subclaim-success";
        return Lang.getMessage(successKey).copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("price", formatPrice(result.price()))
                .add("size", result.size());
    }

    private Message removeArea(ICommandContext context) {
        if (context.args().size() == 2 && context.getArg(1).equalsIgnoreCase("confirm")) {
            return confirmAreaRemoval(context);
        }
        if (context.args().size() != 1) {
            return Lang.getMessage("command.area-usage");
        }
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }

        AreaAtPlayer areaAtPlayer = findAreaAtPlayer(context);
        if (areaAtPlayer.error() != null) {
            return areaAtPlayer.error();
        }
        Message deleteError = validateAreaDeletion(areaAtPlayer.claim(), areaAtPlayer.area());
        if (deleteError != null) {
            return deleteError;
        }

        PENDING_REMOVALS.put(context.player().getUUID(), new PendingAreaRemoval(
                areaAtPlayer.claim().getUuid(),
                areaAtPlayer.area().areaId(),
                System.currentTimeMillis() + CONFIRM_TIMEOUT_MILLIS
        ));

        return Lang.getMessageList("command.area-remove-confirm-message")
                .copy()
                .add("claim", ClaimCommandFormat.escape(areaAtPlayer.claim().getName()))
                .add("area", ClaimCommandFormat.areaBox(areaAtPlayer.area().box()))
                .add("seconds", TimeUnit.MILLISECONDS.toSeconds(CONFIRM_TIMEOUT_MILLIS))
                .add("confirm", ClaimCommandFormat.rawMessage("command.area-remove-confirm-button")
                        .add("command", "/res area remove confirm")
                        .toFormatted());
    }

    private Message confirmAreaRemoval(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }

        PendingAreaRemoval pendingRemoval = PENDING_REMOVALS.get(context.player().getUUID());
        if (pendingRemoval == null) {
            return Lang.getMessage("command.area-remove-no-pending");
        }
        if (pendingRemoval.expiresAtMillis() < System.currentTimeMillis()) {
            PENDING_REMOVALS.remove(context.player().getUUID());
            return Lang.getMessage("command.area-remove-expired");
        }

        Claim claim = ClaimManager.fetchClaim(pendingRemoval.claimUuid());
        if (claim == null) {
            PENDING_REMOVALS.remove(context.player().getUUID());
            return Lang.getMessage("command.claim-not-found").copy()
                    .add("claim", ClaimCommandFormat.shortUuid(pendingRemoval.claimUuid()));
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.area.remove", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        ClaimManager.ClaimAreaInfo area = ClaimManager.fetchClaimAreas(claim.getUuid()).stream()
                .filter(claimArea -> claimArea.areaId() == pendingRemoval.areaId())
                .findFirst()
                .orElse(null);
        if (area == null) {
            PENDING_REMOVALS.remove(context.player().getUUID());
            return Lang.getMessage("command.area-remove-no-area");
        }

        Message deleteError = validateAreaDeletion(claim, area);
        if (deleteError != null) {
            return deleteError;
        }

        claim.removeArea(area.areaId());
        PENDING_REMOVALS.remove(context.player().getUUID());
        return Lang.getMessage("command.area-remove-success").copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("area", ClaimCommandFormat.areaBox(area.box()));
    }

    private Message listAreas(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.area.list", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        List<String> entries = ClaimManager.fetchClaimAreas(claim.getUuid()).stream()
                .map(area -> ClaimCommandFormat.rawMessage("command.area-list-entry")
                        .add("world", ClaimCommandFormat.escape(area.worldUuid()))
                        .add("area", ClaimCommandFormat.areaBox(area.box()))
                        .add("size", area.box().volume())
                        .toString())
                .toList();

        return Lang.getMessageList("command.area-list-message")
                .copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("count", entries.size())
                .add("areas", entries.isEmpty()
                        ? ClaimCommandFormat.raw("command.area-list-empty")
                        : String.join("<newline>", entries));
    }

    private AreaAtPlayer findAreaAtPlayer(ICommandContext context) {
        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return AreaAtPlayer.error(Lang.getMessage("command.not-in-claim"));
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.area.remove", null)) {
            return AreaAtPlayer.error(Lang.getMessage("command.no-claim-admin"));
        }

        cn.jason31416.planetlib.wrapper.SimpleLocation blockLocation = context.player().getLocation().getBlockLocation();
        String worldUuid = context.player().getLocation().getWorld().getBukkitWorld().getUID().toString();
        ClaimManager.ClaimAreaInfo area = ClaimManager.findClaimAreaAt(
                claim.getUuid(),
                worldUuid,
                (int) blockLocation.x(),
                (int) blockLocation.y(),
                (int) blockLocation.z()
        ).orElse(null);
        if (area == null) {
            return AreaAtPlayer.error(Lang.getMessage("command.area-remove-no-area"));
        }
        return new AreaAtPlayer(claim, area, null);
    }

    @Nullable
    private Message validateAreaDeletion(Claim claim, ClaimManager.ClaimAreaInfo area) {
        if (ClaimManager.fetchClaimAreas(claim.getUuid()).size() <= 1) {
            return Lang.getMessage("command.area-remove-last-area");
        }
        if (ClaimManager.hasDescendantAreaOverlap(claim.getUuid(), area.worldUuid(), area.box())) {
            return Lang.getMessage("command.area-remove-has-subclaim");
        }
        return null;
    }

    private AreaValidationResult validateSelectedArea(Claim claim, Player player, SelectionManager.Selection selection) {
        if (!selection.isComplete()) {
            return AreaValidationResult.invalid(AreaValidationReason.INCOMPLETE_SELECTION, null, null, 0L, 0D);
        }
        if (!selection.isSameWorld()) {
            return AreaValidationResult.invalid(AreaValidationReason.DIFFERENT_WORLDS, null, null, 0L, 0D);
        }

        AreaBox areaBox = selection.toAreaBox();
        World world = selection.getWorld();
        if (areaBox == null || world == null) {
            return AreaValidationResult.invalid(AreaValidationReason.INCOMPLETE_SELECTION, null, null, 0L, 0D);
        }

        long size = areaBox.volume();
        boolean subclaimArea = claim.getParentUuid() != null;
        double price = subclaimArea ? 0D : size * Math.max(0D, Config.getDouble("claim.create.price-per-block"));
        if (subclaimArea && !ClaimManager.isAreaCoveredByClaim(claim.getParentUuid(), world.getUID().toString(), areaBox)) {
            return AreaValidationResult.invalid(AreaValidationReason.OUTSIDE_PARENT, areaBox, world, size, price);
        }

        List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas = ClaimManager.fetchOverlappingClaimAreas(world.getUID().toString(), areaBox);
        if (subclaimArea) {
            Set<String> allowedAncestorUuids = new HashSet<>(ClaimManager.fetchAncestorClaimUuids(claim.getUuid()));
            boolean hasDisallowedOverlap = overlappingAreas.stream()
                    .anyMatch(area -> !allowedAncestorUuids.contains(area.claimUuid()));
            if (hasDisallowedOverlap) {
                return AreaValidationResult.invalid(AreaValidationReason.OVERLAP, areaBox, world, size, price, createConflictText(overlappingAreas));
            }
        } else if (!overlappingAreas.isEmpty()) {
            return AreaValidationResult.invalid(AreaValidationReason.OVERLAP, areaBox, world, size, price, createConflictText(overlappingAreas));
        }
        if (price > 0D && SimplePlayer.of(player).getBalance() < price) {
            return AreaValidationResult.invalid(AreaValidationReason.NOT_ENOUGH_MONEY, areaBox, world, size, price);
        }

        return AreaValidationResult.valid(areaBox, world, size, price);
    }

    private Message createAreaAddValidationError(AreaValidationResult result) {
        String key = switch (result.reason()) {
            case INCOMPLETE_SELECTION -> "command.area-add-no-selection";
            case DIFFERENT_WORLDS -> "command.area-add-different-worlds";
            case OUTSIDE_PARENT -> "command.area-add-outside-parent";
            case OVERLAP -> "command.area-add-overlap";
            case NOT_ENOUGH_MONEY -> "command.area-add-not-enough-money";
            default -> "command.area-add-unavailable";
        };
        return Lang.getMessage(key).copy()
                .add("price", formatPrice(result.price()))
                .add("size", result.size())
                .add("conflict", result.conflict());
    }

    private String createConflictText(List<ClaimManager.OverlappingClaimAreaInfo> overlappingAreas) {
        List<String> claimNames = overlappingAreas.stream()
                .map(ClaimManager.OverlappingClaimAreaInfo::claimName)
                .distinct()
                .toList();
        if (claimNames.size() <= 1) {
            return claimNames.getFirst();
        }
        return claimNames.getFirst() + " +" + (claimNames.size() - 1);
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return Long.toString((long) price);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", price);
    }

    private enum AreaValidationReason {
        NONE,
        INCOMPLETE_SELECTION,
        DIFFERENT_WORLDS,
        OUTSIDE_PARENT,
        OVERLAP,
        NOT_ENOUGH_MONEY
    }

    private record AreaValidationResult(boolean valid, AreaValidationReason reason, @Nullable AreaBox areaBox,
                                        @Nullable World world, long size, double price, String conflict) {
        private static AreaValidationResult valid(AreaBox areaBox, World world, long size, double price) {
            return new AreaValidationResult(true, AreaValidationReason.NONE, areaBox, world, size, price, "");
        }

        private static AreaValidationResult invalid(AreaValidationReason reason, @Nullable AreaBox areaBox,
                                                    @Nullable World world, long size, double price) {
            return invalid(reason, areaBox, world, size, price, "");
        }

        private static AreaValidationResult invalid(AreaValidationReason reason, @Nullable AreaBox areaBox,
                                                    @Nullable World world, long size, double price, String conflict) {
            return new AreaValidationResult(false, reason, areaBox, world, size, price, conflict);
        }
    }

    private record AreaAtPlayer(@Nullable Claim claim, @Nullable ClaimManager.ClaimAreaInfo area, @Nullable Message error) {
        private static AreaAtPlayer error(Message error) {
            return new AreaAtPlayer(null, null, error);
        }
    }

    private record PendingAreaRemoval(String claimUuid, int areaId, long expiresAtMillis) {
    }
}
