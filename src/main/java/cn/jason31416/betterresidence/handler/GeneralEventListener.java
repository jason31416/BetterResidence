package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.claim.AreaBox;
import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.betterresidence.visual.AreaBoxVisualizerManager;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.message.StringMessage;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

public class GeneralEventListener implements Listener {
    private static final String ENTER_MESSAGE_FLAG = "enter-message";
    private static final String LEAVE_MESSAGE_FLAG = "leave-message";

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        AreaBoxVisualizerManager.create(event.getPlayer());

        //todo test
        AreaBoxVisualizerManager.addBox(
                event.getPlayer(),
                SimpleWorld.defaultWorld().getBukkitWorld(),
                new AreaBox(-5, 50, 0, 300, -1000, 1000),
                Color.fromRGB(125, 150, 150),
                Color.fromRGB(150, 255, 200)
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AreaBoxVisualizerManager.remove(event.getPlayer());
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
            return;
        }

        getClaimMessage(fromClaim, LEAVE_MESSAGE_FLAG, "claim.left", player).sendActionbar(player);
    }

    private Message getClaimMessage(Claim claim, String flag, String defaultMessageKey, Player player) {
        String rawMessage = claim.getStringFlag(flag, "");
        Message message = rawMessage.isBlank() ? Lang.getMessage(defaultMessageKey) : new StringMessage(rawMessage);
        return message.copy()
                .add("claim", claim.getName())
                .add("owner", claim.getOwner().getName())
                .add("player", player.getName());
    }

    private boolean isSameBlock(Location from, Location to) {
        return Objects.equals(from.getWorld(), to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
