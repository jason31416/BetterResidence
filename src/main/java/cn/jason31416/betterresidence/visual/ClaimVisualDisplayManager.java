package cn.jason31416.betterresidence.visual;

import cn.jason31416.betterresidence.claim.AreaBox;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.util.Config;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ClaimVisualDisplayManager {
    private static final Map<UUID, PlayerDisplayState> STATES = new HashMap<>();

    private ClaimVisualDisplayManager() {
    }

    public static void refreshNearbyClaims(Player player) {
        PlayerDisplayState state = getState(player);
        if (!Config.getBoolean("visualizer.nearby-claims.enabled", true)) {
            clearNearbyClaims(player);
            return;
        }

        Location location = player.getLocation();
        long now = System.currentTimeMillis();
        int refreshMillis = Math.max(250, Config.getInt("visualizer.nearby-claims.refresh-millis", 2000));
        double refreshDistance = Math.max(1D, Config.getDouble("visualizer.nearby-claims.refresh-distance", 8D));
        if (!state.shouldRefreshNearby(location, now, refreshMillis, refreshDistance)) {
            return;
        }

        clearNearbyClaims(player);
        state.updateLastNearbyRefresh(location, now);

        int queryRange = Math.max(1, Config.getInt("visualizer.nearby-claims.query-range", 48));
        int maxAreas = Math.max(1, Config.getInt("visualizer.nearby-claims.max-areas-per-player", 32));
        AreaBox queryBox = createQueryBox(location, queryRange);
        String worldUuid = location.getWorld().getUID().toString();
        List<ClaimManager.OverlappingClaimAreaInfo> areas = ClaimManager.fetchClaimAreasNear(worldUuid, queryBox, maxAreas);
        if (areas.isEmpty()) {
            return;
        }

        Color frameColor = getColor("visualizer.nearby-claims.frame-color");
        Color sideColor = getColor("visualizer.nearby-claims.side-color");
        World world = location.getWorld();
        for (ClaimManager.OverlappingClaimAreaInfo area : areas) {
            state.nearbyBoxes.add(AreaBoxVisualizerManager.addBox(player, world, area.box(), frameColor, sideColor));
        }
    }

    public static void clearNearbyClaims(Player player) {
        PlayerDisplayState state = STATES.get(player.getUniqueId());
        if (state == null || state.nearbyBoxes.isEmpty()) {
            return;
        }

        state.nearbyBoxes.forEach(box -> AreaBoxVisualizerManager.removeBox(player, box));
        state.nearbyBoxes.clear();
    }

    public static void flashClaim(Player player, ClaimManager.ClaimAreaInfo area) {
        if (!Config.getBoolean("visualizer.claim-flash.enabled", true)) {
            return;
        }

        World world = player.getServer().getWorld(UUID.fromString(area.worldUuid()));
        if (world == null) {
            return;
        }

        PlayerDisplayState state = getState(player);
        Color frameColor = getColor("visualizer.claim-flash.frame-color");
        Color sideColor = getColor("visualizer.claim-flash.side-color");
        long expiresAt = System.currentTimeMillis() + Math.max(250, Config.getInt("visualizer.claim-flash.duration-millis", 1500));
        AreaBoxVisualizer.RenderedAreaBox box = AreaBoxVisualizerManager.addBox(player, world, area.box(), frameColor, sideColor);
        state.flashBoxes.add(new TimedBox(box, expiresAt));
    }

    public static void refreshFlashClaims(Player player) {
        PlayerDisplayState state = STATES.get(player.getUniqueId());
        if (state == null || state.flashBoxes.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<TimedBox> iterator = state.flashBoxes.iterator();
        while (iterator.hasNext()) {
            TimedBox timedBox = iterator.next();
            if (timedBox.expiresAt() > now) {
                continue;
            }
            AreaBoxVisualizerManager.removeBox(player, timedBox.box());
            iterator.remove();
        }
    }

    public static void clear(Player player) {
        PlayerDisplayState state = STATES.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        state.nearbyBoxes.forEach(box -> AreaBoxVisualizerManager.removeBox(player, box));
        state.flashBoxes.forEach(timedBox -> AreaBoxVisualizerManager.removeBox(player, timedBox.box()));
    }

    private static PlayerDisplayState getState(Player player) {
        return STATES.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerDisplayState());
    }

    private static AreaBox createQueryBox(Location location, int range) {
        return new AreaBox(
                location.getBlockX() - range,
                location.getBlockX() + range,
                location.getBlockY() - range,
                location.getBlockY() + range,
                location.getBlockZ() - range,
                location.getBlockZ() + range
        );
    }

    private static Color getColor(String path) {
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

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class PlayerDisplayState {
        private final List<AreaBoxVisualizer.RenderedAreaBox> nearbyBoxes = new ArrayList<>();
        private final List<TimedBox> flashBoxes = new ArrayList<>();

        private UUID lastNearbyWorld;
        private int lastNearbyX;
        private int lastNearbyY;
        private int lastNearbyZ;
        private long lastNearbyRefreshMillis;

        private boolean shouldRefreshNearby(Location location, long now, int refreshMillis, double refreshDistance) {
            UUID worldUuid = location.getWorld().getUID();
            if (!Objects.equals(lastNearbyWorld, worldUuid)) {
                return true;
            }
            if (lastNearbyRefreshMillis <= 0L || now - lastNearbyRefreshMillis >= refreshMillis) {
                return true;
            }

            double distanceSquared = square(location.getBlockX() - lastNearbyX)
                    + square(location.getBlockY() - lastNearbyY)
                    + square(location.getBlockZ() - lastNearbyZ);
            return distanceSquared >= refreshDistance * refreshDistance;
        }

        private void updateLastNearbyRefresh(Location location, long now) {
            lastNearbyWorld = location.getWorld().getUID();
            lastNearbyX = location.getBlockX();
            lastNearbyY = location.getBlockY();
            lastNearbyZ = location.getBlockZ();
            lastNearbyRefreshMillis = now;
        }

        private double square(double value) {
            return value * value;
        }
    }

    private record TimedBox(AreaBoxVisualizer.RenderedAreaBox box, long expiresAt) {
    }
}
