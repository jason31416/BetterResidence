package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TpCommand extends ChildCommand {
    static final String TELEPORT_LOCATION_FLAG = "teleport-location";
    private static final String TELEPORT_PERMISSION = "teleport";
    private static final Map<UUID, PendingTeleport> PENDING_TELEPORTS = new HashMap<>();

    public TpCommand(IParentCommand parent) {
        super("tp", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (context.args().size() != 1) {
            return Lang.getMessage("command.tp-usage");
        }

        Claim claim = ClaimManager.resolveClaim(context.getArg(0));
        if (claim == null) {
            return Lang.getMessage("command.claim-not-found").copy()
                    .add("claim", ClaimCommandFormat.escape(context.getArg(0)));
        }

        TeleportDestination destination = resolveDestination(context.player(), claim);
        if (destination.error() != null) {
            return destination.error();
        }

        int warmupSeconds = Math.max(0, Config.getInt("claim.teleport.warmup-seconds", 3));
        Player player = context.player().getPlayer();
        PendingTeleport pendingTeleport = new PendingTeleport(
                claim.getUuid(),
                destination.location(),
                player.getLocation().clone(),
                UUID.randomUUID()
        );
        PENDING_TELEPORTS.put(player.getUniqueId(), pendingTeleport);

        if (warmupSeconds <= 0) {
            completeTeleport(player, pendingTeleport);
            return null;
        }

        PlanetLib.getScheduler().runLater(task -> completeTeleport(player, pendingTeleport), warmupSeconds, TimeUnit.SECONDS);
        return Lang.getMessage("command.tp-warmup").copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("seconds", warmupSeconds);
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.player() == null || context.args().size() != 1) {
            return List.of();
        }
        String prefix = context.getArg(0).toLowerCase(Locale.ROOT);
        return ClaimManager.fetchClaimsByOwner(context.player().getUUID()).stream()
                .map(Claim::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    public static boolean cancelWarmupIfMoved(Player player, Location from, Location to) {
        if (isSameBlock(from, to)) {
            return false;
        }
        PendingTeleport pendingTeleport = PENDING_TELEPORTS.remove(player.getUniqueId());
        if (pendingTeleport == null) {
            return false;
        }
        Lang.getMessage("command.tp-cancelled-moved").send(player);
        return true;
    }

    public static void cancelWarmup(Player player) {
        PENDING_TELEPORTS.remove(player.getUniqueId());
    }

    static String formatTeleportLocation(Location location) {
        return String.format(Locale.ROOT,
                "%s %.6f %.6f %.6f %.6f %.6f",
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    static @Nullable Location parseTeleportLocation(Player player, String value) {
        String[] parts = value.split(" ");
        if (parts.length != 6) {
            return null;
        }
        try {
            World world = player.getServer().getWorld(UUID.fromString(parts[0]));
            if (world == null) {
                return null;
            }
            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5])
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isInsideClaim(Location location, Claim claim) {
        Claim locationClaim = ClaimManager.findClaimAt(SimpleLocation.of(location));
        return locationClaim != null && locationClaim.getUuid().equals(claim.getUuid());
    }

    private TeleportDestination resolveDestination(SimplePlayer player, Claim claim) {
        if (!claim.checkPlayerPermission(player, TELEPORT_PERMISSION, null)) {
            return TeleportDestination.error(Lang.getMessage("command.no-claim-permission"));
        }

        String storedLocation = claim.getStringFlag(TELEPORT_LOCATION_FLAG, "");
        if (storedLocation.isBlank()) {
            return TeleportDestination.error(Lang.getMessage("command.tp-location-not-set"));
        }

        Location location = parseTeleportLocation(player.getPlayer(), storedLocation);
        if (location == null) {
            return TeleportDestination.error(Lang.getMessage("command.tp-location-invalid"));
        }
        if (!isInsideClaim(location, claim)) {
            return TeleportDestination.error(Lang.getMessage("command.tp-location-outside"));
        }

        return TeleportDestination.success(location);
    }

    private void completeTeleport(Player player, PendingTeleport pendingTeleport) {
        PendingTeleport current = PENDING_TELEPORTS.get(player.getUniqueId());
        if (current == null || !current.id().equals(pendingTeleport.id())) {
            return;
        }
        PENDING_TELEPORTS.remove(player.getUniqueId());

        if (!player.isOnline()) {
            return;
        }
        if (!isSameBlock(pendingTeleport.startLocation(), player.getLocation())) {
            Lang.getMessage("command.tp-cancelled-moved").send(player);
            return;
        }

        Claim claim = ClaimManager.fetchClaim(pendingTeleport.claimUuid());
        if (claim == null) {
            Lang.getMessage("command.tp-cancelled").send(player);
            return;
        }

            TeleportDestination destination = resolveDestination(SimplePlayer.of(player), claim);
        if (destination.error() != null) {
            destination.error().send(player);
            return;
        }

        SimplePlayer.of(player).teleport(SimpleLocation.of(destination.location()))
                .thenAccept(success -> {
                    if (success) {
                        Lang.getMessage("command.tp-success").copy()
                                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                                .send(player);
                    } else {
                        Lang.getMessage("command.tp-cancelled").send(player);
                    }
                });
    }

    private static boolean isSameBlock(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private record PendingTeleport(String claimUuid, Location destination, Location startLocation, UUID id) {
    }

    private record TeleportDestination(@Nullable Location location, @Nullable Message error) {
        static TeleportDestination success(Location location) {
            return new TeleportDestination(location, null);
        }

        static TeleportDestination error(Message error) {
            return new TeleportDestination(null, error);
        }
    }
}
