package cn.jason31416.betterresidence.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FlagRegistry {

    private static final Map<String, RegisteredFlag> registry = new LinkedHashMap<>();

    private FlagRegistry() {
    }

    public static void registerFlag(String id, FlagValueType type, String defaultValue) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Flag id cannot be blank");
        }
        RegisteredFlag flag = new RegisteredFlag(id, type, defaultValue);
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

    static {
        registerFlag("enter-message", FlagValueType.string(), "");
        registerFlag("leave-message", FlagValueType.string(), "");
    }

    public record RegisteredFlag(String id, FlagValueType type, String defaultValue) {
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
