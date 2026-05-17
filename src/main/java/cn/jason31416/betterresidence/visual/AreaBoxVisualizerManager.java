package cn.jason31416.betterresidence.visual;

import cn.jason31416.betterresidence.claim.AreaBox;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AreaBoxVisualizerManager {
    private static final Map<UUID, AreaBoxVisualizer> VISUALIZERS = new HashMap<>();

    private AreaBoxVisualizerManager() {
    }

    /**
     * Create a particle visualizer for the player if one does not already exist.
     *
     * @param player The player that owns the visualizer.
     */
    public static void create(Player player) {
        VISUALIZERS.computeIfAbsent(player.getUniqueId(), ignored -> new AreaBoxVisualizer(player));
    }

    /**
     * Remove and destroy the player's visualizer.
     *
     * @param player The player whose visualizer should be removed.
     */
    public static void remove(Player player) {
        AreaBoxVisualizer visualizer = VISUALIZERS.remove(player.getUniqueId());
        if (visualizer != null) {
            visualizer.destroy();
        }
    }

    /**
     * Add a box to the player's visualizer.
     * <p>
     * The visualizer is created automatically if it does not already exist. The box remains registered until
     * {@link #clearBoxes(Player)}, {@link #remove(Player)}, or {@link #shutdown()} is called; boxes that are outside
     * the configured particle range are skipped during rendering rather than removed.
     *
     * @param player The player who should see the particles.
     * @param world The world the box belongs to.
     * @param areaBox The block-aligned area bounds to render.
     * @param frameColor The color used for box edges.
     * @param sideColor The color used for box faces.
     * @return The rendered area box object, which can then be used to remove from the list.
     */
    public static AreaBoxVisualizer.RenderedAreaBox addBox(Player player, World world, AreaBox areaBox, Color frameColor, Color sideColor) {
        create(player);
        return VISUALIZERS.get(player.getUniqueId()).addBox(world, areaBox, frameColor, sideColor);
    }

    /**
     * Remove one registered box without affecting other boxes owned by the same player.
     */
    public static void removeBox(Player player, AreaBoxVisualizer.RenderedAreaBox box) {
        AreaBoxVisualizer visualizer = VISUALIZERS.get(player.getUniqueId());
        if (visualizer != null) {
            visualizer.removeBox(box);
        }
    }

    /**
     * Remove all boxes from the player's visualizer without destroying the visualizer task.
     *
     * @param player The player whose visible boxes should be cleared.
     */
    public static void clearBoxes(Player player) {
        AreaBoxVisualizer visualizer = VISUALIZERS.get(player.getUniqueId());
        if (visualizer != null) {
            visualizer.clearBoxes();
        }
    }

    /**
     * Destroy every active visualizer and clear all registered boxes.
     */
    public static void shutdown() {
        for (AreaBoxVisualizer visualizer : VISUALIZERS.values()) {
            visualizer.destroy();
        }
        VISUALIZERS.clear();
    }
}
