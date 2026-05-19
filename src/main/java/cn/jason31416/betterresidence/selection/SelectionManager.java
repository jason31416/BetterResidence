package cn.jason31416.betterresidence.selection;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.visual.AreaBoxVisualizer;
import cn.jason31416.betterresidence.visual.AreaBoxVisualizerManager;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SelectionManager {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    private SelectionManager() {
    }

    public static Selection getSelection(Player player) {
        return SELECTIONS.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
    }

    @Nullable
    public static Selection getExistingSelection(Player player) {
        return SELECTIONS.get(player.getUniqueId());
    }

    public static void setPosition(Player player, int index, SimpleLocation location) {
        Selection selection = getSelection(player);
        if (index == 1) {
            selection.pos1 = location;
        } else if (index == 2) {
            selection.pos2 = location;
        } else {
            throw new IllegalArgumentException("Selection position index must be 1 or 2");
        }
    }

    public static void clearSelection(Player player) {
        removeSelectionBox(player);
        SELECTIONS.remove(player.getUniqueId());
    }

    public static void removeSelectionBox(Player player) {
        Selection selection = SELECTIONS.get(player.getUniqueId());
        if (selection == null || selection.renderedBox == null) {
            return;
        }

        // Only remove the claim-creation preview box; other visualizer boxes may be owned by other features.
        AreaBoxVisualizerManager.removeBox(player, selection.renderedBox);
        selection.renderedBox = null;
        selection.lastRenderedAreaBox = null;
        selection.lastRenderedWorld = null;
        selection.lastFrameColor = null;
        selection.lastSideColor = null;
    }

    public static void showSelectionBox(Player player, World world, AreaBox areaBox, Color frameColor, Color sideColor) {
        Selection selection = getSelection(player);
        if (selection.renderedBox != null
                && Objects.equals(selection.lastRenderedWorld, world)
                && Objects.equals(selection.lastRenderedAreaBox, areaBox)
                && Objects.equals(selection.lastFrameColor, frameColor)
                && Objects.equals(selection.lastSideColor, sideColor)) {
            return;
        }

        removeSelectionBox(player);
        selection.renderedBox = AreaBoxVisualizerManager.addBox(player, world, areaBox, frameColor, sideColor);
        selection.lastRenderedWorld = world;
        selection.lastRenderedAreaBox = areaBox;
        selection.lastFrameColor = frameColor;
        selection.lastSideColor = sideColor;
    }

    public static class Selection {
        @Nullable
        private SimpleLocation pos1;
        @Nullable
        private SimpleLocation pos2;
        @Nullable
        private AreaBoxVisualizer.RenderedAreaBox renderedBox;
        @Nullable
        private AreaBox lastRenderedAreaBox;
        @Nullable
        private World lastRenderedWorld;
        @Nullable
        private Color lastFrameColor;
        @Nullable
        private Color lastSideColor;

        @Nullable
        public SimpleLocation getPos1() {
            return pos1;
        }

        @Nullable
        public SimpleLocation getPos2() {
            return pos2;
        }

        public boolean hasAnyPosition() {
            return pos1 != null || pos2 != null;
        }

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }

        public boolean isSameWorld() {
            return isComplete() && pos1.world().equals(pos2.world());
        }

        @Nullable
        public AreaBox toAreaBox() {
            if (!isSameWorld()) {
                return null;
            }

            int x1 = (int) pos1.getBlockLocation().x();
            int y1 = (int) pos1.getBlockLocation().y();
            int z1 = (int) pos1.getBlockLocation().z();
            int x2 = (int) pos2.getBlockLocation().x();
            int y2 = (int) pos2.getBlockLocation().y();
            int z2 = (int) pos2.getBlockLocation().z();
            return new AreaBox(
                    Math.min(x1, x2),
                    Math.max(x1, x2),
                    Math.min(y1, y2),
                    Math.max(y1, y2),
                    Math.min(z1, z2),
                    Math.max(z1, z2)
            );
        }

        @Nullable
        public World getWorld() {
            if (!isSameWorld()) {
                return null;
            }
            return pos1.world().getBukkitWorld();
        }
    }
}
