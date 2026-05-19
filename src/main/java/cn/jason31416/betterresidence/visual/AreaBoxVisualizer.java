package cn.jason31416.betterresidence.visual;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.lib.folialib.wrapper.task.WrappedTask;
import cn.jason31416.planetlib.util.Config;
import lombok.Getter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AreaBoxVisualizer {
    private static final int RENDER_PERIOD_TICKS = 10;
    private static final int DEFAULT_RANGE = 16;
    private static final double FRAME_SPACING = 1.0D;
    private static final double SIDE_SPACING = 2.0D;
    private static final float FRAME_PARTICLE_SIZE = 1.5F;
    private static final float SIDE_PARTICLE_SIZE = 1.1F;

    private final Player player;
    @Getter
    private final List<RenderedAreaBox> boxes = new ArrayList<>();

    private WrappedTask task;

    public AreaBoxVisualizer(Player player) {
        this.player = player;
        this.task = PlanetLib.getScheduler().runAtEntityTimer(player, this::render, 0L, RENDER_PERIOD_TICKS);
    }

    public RenderedAreaBox addBox(World world, AreaBox areaBox, Color frameColor, Color sideColor) {
        RenderedAreaBox box = new RenderedAreaBox(world, areaBox, frameColor, sideColor);
        boxes.add(box);
        return box;
    }

    public void clearBoxes() {
        boxes.clear();
    }

    public void removeBox(RenderedAreaBox box) {
        boxes.remove(box);
    }

    public void destroy() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        boxes.clear();
    }

    private void render() {
        if (!player.isOnline()) {
            destroy();
            return;
        }

        if (boxes.isEmpty()) {
            return;
        }

        World playerWorld = player.getWorld();
        RenderWindow window = RenderWindow.around(player.getLocation(), Math.max(1, getRenderRange()));

        for (RenderedAreaBox box : boxes) {
            if (!box.isRenderableIn(playerWorld, window)) {
                continue;
            }

            box.renderSides(player, window);
            box.renderFrame(player, window);
        }
    }

    private static double firstAlignedInside(double origin, double min, double spacing) {
        if (origin >= min) {
            return origin;
        }
        return origin + Math.ceil((min - origin) / spacing) * spacing;
    }

    private static int getRenderRange() {
        if (Config.contains("visualizer.range")) {
            return Config.getInt("visualizer.range");
        }
        return Config.getInt("particle.range", DEFAULT_RANGE);
    }

    private record RenderWindow(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        private static RenderWindow around(Location location, double range) {
            return new RenderWindow(
                    location.getX() - range,
                    location.getX() + range,
                    location.getY() - range,
                    location.getY() + range,
                    location.getZ() - range,
                    location.getZ() + range
            );
        }

        private boolean containsX(double x) {
            return minX <= x && x <= maxX;
        }

        private boolean containsY(double y) {
            return minY <= y && y <= maxY;
        }

        private boolean containsZ(double z) {
            return minZ <= z && z <= maxZ;
        }
    }

    public static class RenderedAreaBox {
        private final World world;
        private final AreaBox areaBox;
        private final Particle.DustOptions frameParticle;
        private final Particle.DustOptions sideParticle;

        private RenderedAreaBox(World world, AreaBox areaBox, Color frameColor, Color sideColor) {
            this.world = world;
            this.areaBox = areaBox;
            this.frameParticle = new Particle.DustOptions(frameColor, FRAME_PARTICLE_SIZE);
            this.sideParticle = new Particle.DustOptions(sideColor, SIDE_PARTICLE_SIZE);
        }

        private boolean isRenderableIn(World playerWorld, RenderWindow window) {
            if (world != playerWorld) {
                return false;
            }

            return areaBox.maxX() + 1D >= window.minX() && areaBox.minX() <= window.maxX()
                    && areaBox.maxY() + 1D >= window.minY() && areaBox.minY() <= window.maxY()
                    && areaBox.maxZ() + 1D >= window.minZ() && areaBox.minZ() <= window.maxZ();
        }

        private void renderSides(Player player, RenderWindow window) {
            double minX = areaBox.minX();
            double maxX = areaBox.maxX() + 1D;
            double minY = areaBox.minY();
            double maxY = areaBox.maxY() + 1D;
            double minZ = areaBox.minZ();
            double maxZ = areaBox.maxZ() + 1D;

            renderZPlane(player, window, minX, maxX, minY, maxY, minZ);
            renderZPlane(player, window, minX, maxX, minY, maxY, maxZ);
            renderXPlane(player, window, minZ, maxZ, minY, maxY, minX);
            renderXPlane(player, window, minZ, maxZ, minY, maxY, maxX);
            renderYPlane(player, window, minX, maxX, minZ, maxZ, minY);
            renderYPlane(player, window, minX, maxX, minZ, maxZ, maxY);
        }

        private void renderFrame(Player player, RenderWindow window) {
            double minX = areaBox.minX();
            double maxX = areaBox.maxX() + 1D;
            double minY = areaBox.minY();
            double maxY = areaBox.maxY() + 1D;
            double minZ = areaBox.minZ();
            double maxZ = areaBox.maxZ() + 1D;

            renderXLine(player, window, minX, maxX, minY, minZ);
            renderXLine(player, window, minX, maxX, maxY, minZ);
            renderXLine(player, window, minX, maxX, minY, maxZ);
            renderXLine(player, window, minX, maxX, maxY, maxZ);

            renderYLine(player, window, minY, maxY, minX, minZ);
            renderYLine(player, window, minY, maxY, maxX, minZ);
            renderYLine(player, window, minY, maxY, minX, maxZ);
            renderYLine(player, window, minY, maxY, maxX, maxZ);

            renderZLine(player, window, minZ, maxZ, minX, minY);
            renderZLine(player, window, minZ, maxZ, maxX, minY);
            renderZLine(player, window, minZ, maxZ, minX, maxY);
            renderZLine(player, window, minZ, maxZ, maxX, maxY);
        }

        private void renderZPlane(Player player, RenderWindow window, double minX, double maxX, double minY, double maxY, double z) {
            if (!window.containsZ(z)) {
                return;
            }

            double visibleMinX = Math.max(minX + SIDE_SPACING, window.minX());
            double visibleMaxX = Math.min(maxX, window.maxX());
            double visibleMinY = Math.max(minY + SIDE_SPACING, window.minY());
            double visibleMaxY = Math.min(maxY, window.maxY());
            double startX = firstAlignedInside(minX + SIDE_SPACING, visibleMinX, SIDE_SPACING);
            double startY = firstAlignedInside(minY + SIDE_SPACING, visibleMinY, SIDE_SPACING);

            for (double x = startX; x < visibleMaxX; x += SIDE_SPACING) {
                for (double y = startY; y < visibleMaxY; y += SIDE_SPACING) {
                    spawnParticle(player, x, y, z, sideParticle);
                }
            }
        }

        private void renderXPlane(Player player, RenderWindow window, double minZ, double maxZ, double minY, double maxY, double x) {
            if (!window.containsX(x)) {
                return;
            }

            double visibleMinZ = Math.max(minZ + SIDE_SPACING, window.minZ());
            double visibleMaxZ = Math.min(maxZ, window.maxZ());
            double visibleMinY = Math.max(minY + SIDE_SPACING, window.minY());
            double visibleMaxY = Math.min(maxY, window.maxY());
            double startZ = firstAlignedInside(minZ + SIDE_SPACING, visibleMinZ, SIDE_SPACING);
            double startY = firstAlignedInside(minY + SIDE_SPACING, visibleMinY, SIDE_SPACING);

            for (double z = startZ; z < visibleMaxZ; z += SIDE_SPACING) {
                for (double y = startY; y < visibleMaxY; y += SIDE_SPACING) {
                    spawnParticle(player, x, y, z, sideParticle);
                }
            }
        }

        private void renderYPlane(Player player, RenderWindow window, double minX, double maxX, double minZ, double maxZ, double y) {
            if (!window.containsY(y)) {
                return;
            }

            double visibleMinX = Math.max(minX + SIDE_SPACING, window.minX());
            double visibleMaxX = Math.min(maxX, window.maxX());
            double visibleMinZ = Math.max(minZ + SIDE_SPACING, window.minZ());
            double visibleMaxZ = Math.min(maxZ, window.maxZ());
            double startX = firstAlignedInside(minX + SIDE_SPACING, visibleMinX, SIDE_SPACING);
            double startZ = firstAlignedInside(minZ + SIDE_SPACING, visibleMinZ, SIDE_SPACING);

            for (double x = startX; x < visibleMaxX; x += SIDE_SPACING) {
                for (double z = startZ; z < visibleMaxZ; z += SIDE_SPACING) {
                    spawnParticle(player, x, y, z, sideParticle);
                }
            }
        }

        private void renderXLine(Player player, RenderWindow window, double minX, double maxX, double y, double z) {
            if (!window.containsY(y) || !window.containsZ(z)) {
                return;
            }

            double visibleMinX = Math.max(minX, window.minX());
            double visibleMaxX = Math.min(maxX, window.maxX());
            double startX = firstAlignedInside(minX, visibleMinX, FRAME_SPACING);
            for (double x = startX; x <= visibleMaxX; x += FRAME_SPACING) {
                spawnParticle(player, x, y, z, frameParticle);
            }
        }

        private void renderYLine(Player player, RenderWindow window, double minY, double maxY, double x, double z) {
            if (!window.containsX(x) || !window.containsZ(z)) {
                return;
            }

            double visibleMinY = Math.max(minY, window.minY());
            double visibleMaxY = Math.min(maxY, window.maxY());
            double startY = firstAlignedInside(minY, visibleMinY, FRAME_SPACING);
            for (double y = startY; y <= visibleMaxY; y += FRAME_SPACING) {
                spawnParticle(player, x, y, z, frameParticle);
            }
        }

        private void renderZLine(Player player, RenderWindow window, double minZ, double maxZ, double x, double y) {
            if (!window.containsX(x) || !window.containsY(y)) {
                return;
            }

            double visibleMinZ = Math.max(minZ, window.minZ());
            double visibleMaxZ = Math.min(maxZ, window.maxZ());
            double startZ = firstAlignedInside(minZ, visibleMinZ, FRAME_SPACING);
            for (double z = startZ; z <= visibleMaxZ; z += FRAME_SPACING) {
                spawnParticle(player, x, y, z, frameParticle);
            }
        }

        private void spawnParticle(Player player, double x, double y, double z, Particle.DustOptions options) {
            player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0D, 0D, 0D, 0D, options);
        }
    }
}
