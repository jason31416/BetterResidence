package cn.jason31416.betterresidence.core;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.Monster;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FlagRegistry {

    private static final Map<String, RegisteredFlag> registry = new LinkedHashMap<>();

    private FlagRegistry() {
    }

    public static void registerFlag(String id, FlagValueType type, String defaultValue) {
        registerFlag(id, type, defaultValue, null);
    }

    public static void registerFlag(String id, FlagValueType type, String defaultValue, String parentId) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Flag id cannot be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Flag type cannot be null");
        }
        if (parentId != null) {
            if (parentId.isBlank()) {
                throw new IllegalArgumentException("Flag parent id cannot be blank");
            }
            if (parentId.equals(id)) {
                throw new IllegalArgumentException("Flag cannot be its own parent: " + id);
            }
            RegisteredFlag parent = registry.get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Unknown parent flag " + parentId + " for flag " + id);
            }
            if (!type.equals(parent.type())) {
                throw new IllegalArgumentException("Flag " + id + " type must match parent flag " + parentId);
            }
            ensureNoParentCycle(id, parentId);
        }

        RegisteredFlag flag = new RegisteredFlag(id, type, defaultValue, parentId);
        flag.validate(defaultValue);
        registry.put(id, flag);
    }

    public static Collection<RegisteredFlag> getFlags() {
        return registry.values();
    }

    public static List<String> getFlagIds() {
        return List.copyOf(registry.keySet());
    }

    public static Optional<RegisteredFlag> getFlag(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public static boolean isRegistered(String id) {
        return registry.containsKey(id);
    }

    public static List<RegisteredFlag> getFlagAndParents(RegisteredFlag flag) {
        List<RegisteredFlag> flags = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();
        RegisteredFlag current = flag;
        while (current != null) {
            if (!seen.add(current.id())) {
                throw new IllegalStateException("Flag parent cycle detected at " + current.id());
            }
            flags.add(current);
            current = current.parentId() == null ? null : registry.get(current.parentId());
        }
        return List.copyOf(flags);
    }

    private static void ensureNoParentCycle(String id, String parentId) {
        Set<String> seen = new HashSet<>();
        seen.add(id);
        String currentId = parentId;
        while (currentId != null) {
            if (!seen.add(currentId)) {
                throw new IllegalArgumentException("Flag parent cycle detected at " + currentId);
            }
            RegisteredFlag current = registry.get(currentId);
            currentId = current == null ? null : current.parentId();
        }
    }

    static {
        registerFlag("enter-message", FlagValueType.string(), "");
        registerFlag("leave-message", FlagValueType.string(), "");
        registerFlag("teleport-location", FlagValueType.string(), "");
        registerFlag("time", FlagValueType.integer(), "-1");
        registerFlag("weather", FlagValueType.options("global", "clear", "rain"), "global");

        FlagValueType flowMode = FlagValueType.options("allow", "internal", "deny");
        registerFlag("flow", flowMode, "internal");
        registerFlag("flow.water", flowMode, "internal", "flow");
        registerFlag("flow.lava", flowMode, "internal", "flow");

        registerFlag("piston", FlagValueType.bool(), "false");
        registerFlag("piston.cross-border", FlagValueType.bool(), "false", "piston");

        registerFlag("explosion", FlagValueType.bool(), "false");
        registerFlag("explosion.tnt", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.creeper", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.fireball", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.wither", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.crystal", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.bed", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.respawn-anchor", FlagValueType.bool(), "false", "explosion");
        registerFlag("explosion.other", FlagValueType.bool(), "false", "explosion");

        registerFlag("fire", FlagValueType.bool(), "false");
        registerFlag("fire.ignite", FlagValueType.bool(), "false", "fire");
        registerFlag("fire.spread", FlagValueType.bool(), "false", "fire");
        registerFlag("fire.burn", FlagValueType.bool(), "false", "fire");

        registerFlag("entity-change-block", FlagValueType.bool(), "false");
        registerFlag("entity-change-block.enderman", FlagValueType.bool(), "false", "entity-change-block");
        registerFlag("entity-change-block.ravager", FlagValueType.bool(), "true", "entity-change-block");
        registerFlag("entity-change-block.sheep", FlagValueType.bool(), "true", "entity-change-block");
        registerFlag("entity-change-block.silverfish", FlagValueType.bool(), "true", "entity-change-block");
        registerFlag("entity-change-block.wither", FlagValueType.bool(), "false", "entity-change-block");
        registerFlag("entity-change-block.other", FlagValueType.bool(), "false", "entity-change-block");

        registerFlag("growth", FlagValueType.bool(), "true");
        registerFlag("growth.crop", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.stem", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.tree", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.grass", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.vine", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.bamboo", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.cactus", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.sugar-cane", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.kelp", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.mushroom", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.chorus", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.amethyst", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.sculk", FlagValueType.bool(), "true", "growth");
        registerFlag("growth.other", FlagValueType.bool(), "true", "growth");

        registerFlag("decay", FlagValueType.bool(), "true");
        registerFlag("decay.leaves", FlagValueType.bool(), "true", "decay");
        registerFlag("decay.ice", FlagValueType.bool(), "true", "decay");
        registerFlag("decay.snow", FlagValueType.bool(), "true", "decay");
        registerFlag("decay.coral", FlagValueType.bool(), "true", "decay");
        registerFlag("decay.farmland", FlagValueType.bool(), "true", "decay");
        registerFlag("decay.other", FlagValueType.bool(), "true", "decay");

        registerFlag("form", FlagValueType.bool(), "true");
        registerFlag("form.snow", FlagValueType.bool(), "true", "form");
        registerFlag("form.ice", FlagValueType.bool(), "true", "form");
        registerFlag("form.stone", FlagValueType.bool(), "true", "form");
        registerFlag("form.obsidian", FlagValueType.bool(), "true", "form");
        registerFlag("form.concrete", FlagValueType.bool(), "true", "form");
        registerFlag("form.other", FlagValueType.bool(), "true", "form");

        registerFlag("portal-create", FlagValueType.bool(), "true");
        registerFlag("portal-create.nether", FlagValueType.bool(), "true", "portal-create");
        registerFlag("portal-create.end", FlagValueType.bool(), "true", "portal-create");
        registerFlag("portal-create.custom", FlagValueType.bool(), "true", "portal-create");

        registerFlag("spawn", FlagValueType.bool(), "true");
        registerFlag("spawn.monster", FlagValueType.bool(), "true", "spawn");
        registerFlag("spawn.animal", FlagValueType.bool(), "true", "spawn");
    }

    public record RegisteredFlag(String id, FlagValueType type, String defaultValue, String parentId) {
        public boolean isValidValue(String value) {
            return type.isValid(value);
        }

        public List<String> tabCompleteValue(String input) {
            return type.tabComplete(input);
        }

        public void validate(String value) {
            if (!isValidValue(value)) {
                throw new IllegalArgumentException("Invalid value for flag " + id + ": " + value);
            }
        }
    }
}
