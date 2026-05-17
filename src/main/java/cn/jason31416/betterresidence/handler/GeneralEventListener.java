package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.betterresidence.claim.AreaBox;
import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.betterresidence.selection.ClaimCreationValidator;
import cn.jason31416.betterresidence.selection.SelectionManager;
import cn.jason31416.betterresidence.visual.AreaBoxVisualizerManager;
import cn.jason31416.betterresidence.visual.ClaimVisualDisplayManager;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.message.StringMessage;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GeneralEventListener implements Listener {
    private static final String ENTER_MESSAGE_FLAG = "enter-message";
    private static final String LEAVE_MESSAGE_FLAG = "leave-message";
    private static final long SELECTION_DISPLAY_PERIOD_MILLIS = 500L;

    public GeneralEventListener() {
        startSelectionDisplayTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        AreaBoxVisualizerManager.create(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        SelectionManager.clearSelection(event.getPlayer());
        ClaimVisualDisplayManager.clear(event.getPlayer());
        AreaBoxVisualizerManager.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || !isHoldingSelectionTool(event.getPlayer())) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            SelectionManager.setPosition(event.getPlayer(), 1, SimpleLocation.of(event.getClickedBlock()));
            Lang.getMessage("claim.selection.position-1-set").sendActionbar(event.getPlayer());
            event.setCancelled(true);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            SelectionManager.setPosition(event.getPlayer(), 2, SimpleLocation.of(event.getClickedBlock()));
            Lang.getMessage("claim.selection.position-2-set").sendActionbar(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || isSameBlock(event.getFrom(), to)) {
            return;
        }

        handleClaimChange(event.getPlayer(), event.getFrom(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        handleClaimChange(event.getPlayer(), event.getFrom(), to);
    }

    private void handleClaimChange(Player player, Location from, Location to) {
        Claim fromClaim = ClaimManager.findClaimAt(SimpleLocation.of(from));
        Claim toClaim = ClaimManager.findClaimAt(SimpleLocation.of(to));

        String fromClaimUuid = fromClaim == null ? null : fromClaim.getUuid();
        String toClaimUuid = toClaim == null ? null : toClaim.getUuid();
        if (Objects.equals(fromClaimUuid, toClaimUuid)) {
            return;
        }

        if (toClaim != null) {
            getClaimMessage(toClaim, ENTER_MESSAGE_FLAG, "claim.entered", player).sendActionbar(player);
        }

        if (fromClaim != null) {
            flashClaimAreas(player, fromClaim);
            if (toClaim == null) {
                getClaimMessage(fromClaim, LEAVE_MESSAGE_FLAG, "claim.left", player).sendActionbar(player);
            }
        }
        if (toClaim != null) {
            flashClaimAreas(player, toClaim);
        }
    }

    private Message getClaimMessage(Claim claim, String flag, String defaultMessageKey, Player player) {
        String rawMessage = claim.getStringFlag(flag, "");
        Message message = rawMessage.isBlank() ? Lang.getMessage(defaultMessageKey) : new StringMessage(rawMessage);
        return message.copy()
                .add("claim", claim.getName())
                .add("owner", claim.getOwner().getName())
                .add("player", player.getName());
    }

    private void startSelectionDisplayTask() {
        // Keep Bukkit player/world access on each player's entity scheduler instead of an async worker.
        PlanetLib.getScheduler().runTimer(task -> {
            for (Player player : BetterResidence.getInstance().getServer().getOnlinePlayers()) {
                PlanetLib.getScheduler().runAtEntity(player, entityTask -> refreshSelectionDisplay(player));
            }
        }, SELECTION_DISPLAY_PERIOD_MILLIS, SELECTION_DISPLAY_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void refreshSelectionDisplay(Player player) {
        ClaimVisualDisplayManager.refreshFlashClaims(player);

        SelectionManager.Selection selection = SelectionManager.getExistingSelection(player);
        if (!isHoldingSelectionTool(player)) {
            ClaimVisualDisplayManager.clearNearbyClaims(player);
            if (selection != null) {
                SelectionManager.removeSelectionBox(player);
            }
            return;
        }

        ClaimVisualDisplayManager.refreshNearbyClaims(player);
        if (selection == null) {
            return;
        }
        if (!selection.hasAnyPosition()) {
            return;
        }

        ClaimCreationValidator.ValidationResult result = ClaimCreationValidator.validate(player, selection);
        if (result.areaBox() != null && result.world() != null) {
            SelectionColors colors = getSelectionColors(result.visualState());
            SelectionManager.showSelectionBox(player, result.world(), result.areaBox(), colors.frameColor(), colors.sideColor());
        } else {
            SelectionManager.removeSelectionBox(player);
        }

        createSelectionActionbar(result, selection).sendActionbar(player);
    }

    private Message createSelectionActionbar(ClaimCreationValidator.ValidationResult result, SelectionManager.Selection selection) {
        String key;
        if (!selection.isComplete()) {
            key = selection.getPos1() == null ? "claim.selection.missing-position-1" : "claim.selection.missing-position-2";
        } else if (result.valid()) {
            key = "claim.selection.available";
        } else {
            key = switch (result.reason()) {
                case DIFFERENT_WORLDS -> "claim.selection.different-worlds";
                case OVERLAP -> "claim.selection.overlap";
                case MAX_CLAIMS -> "claim.selection.max-claims";
                case NOT_ENOUGH_MONEY -> "claim.selection.not-enough-money";
                default -> "claim.selection.unavailable";
            };
        }

        return Lang.getMessage(key).copy()
                .add("size", result.size())
                .add("price", formatPrice(result.price()))
                .add("max", Config.getInt("claim.max-claims-per-player"))
                .add("conflict", result.conflict());
    }

    private void flashClaimAreas(Player player, Claim claim) {
        if(isHoldingSelectionTool(player)) {
            return; // To prevent display overlap
        }
        for (ClaimManager.ClaimAreaInfo area : ClaimManager.fetchClaimAreas(claim.getUuid())) {
            ClaimVisualDisplayManager.flashClaim(player, area);
        }
    }

    private boolean isHoldingSelectionTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType() == getSelectionTool();
    }

    private Material getSelectionTool() {
        Material material = Material.matchMaterial(Config.getString("claim.create.selection-tool"));
        return material == null ? Material.WOODEN_HOE : material;
    }

    private SelectionColors getSelectionColors(ClaimCreationValidator.VisualState state) {
        String key = switch (state) {
            case AVAILABLE -> "available";
            case LIMITED -> "limited";
            case CONFLICT -> "conflict";
        };
        return new SelectionColors(
                getColor("visualizer.create-claim." + key + ".frame-color"),
                getColor("visualizer.create-claim." + key + ".side-color")
        );
    }

    private Color getColor(String path) {
        String value = Config.getString(path);
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return Color.WHITE;
        }
        try {
            return Color.fromRGB(
                    clampColor(Integer.parseInt(parts[0].trim())),
                    clampColor(Integer.parseInt(parts[1].trim())),
                    clampColor(Integer.parseInt(parts[2].trim()))
            );
        } catch (NumberFormatException ignored) {
            return Color.WHITE;
        }
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return Long.toString((long) price);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", price);
    }

    private record SelectionColors(Color frameColor, Color sideColor) {
    }

    private boolean isSameBlock(Location from, Location to) {
        return Objects.equals(from.getWorld(), to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
