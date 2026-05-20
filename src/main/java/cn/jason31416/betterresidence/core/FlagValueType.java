package cn.jason31416.betterresidence.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public interface FlagValueType {
    FlagValueType STRING = Objects::nonNull;

    FlagValueType INTEGER = value -> {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    };

    FlagValueType DECIMAL = value -> {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    };

    FlagValueType BOOL = new OptionsFlagValueType(List.of("true", "false"));

    boolean isValid(String value);

    default List<String> tabComplete(String input) {
        return List.of();
    }

    static FlagValueType string() {
        return STRING;
    }

    static FlagValueType integer() {
        return INTEGER;
    }

    static FlagValueType decimal() {
        return DECIMAL;
    }

    static FlagValueType bool() {
        return BOOL;
    }

    static FlagValueType options(String... options) {
        return new OptionsFlagValueType(List.of(options));
    }

    record OptionsFlagValueType(List<String> options) implements FlagValueType {
        public OptionsFlagValueType {
            options = List.copyOf(options);
            if (options.isEmpty()) {
                throw new IllegalArgumentException("Flag options cannot be empty");
            }
            if (options.stream().anyMatch(option -> option == null || option.isBlank())) {
                throw new IllegalArgumentException("Flag options cannot contain blank values");
            }
            if (options.stream().distinct().count() != options.size()) {
                throw new IllegalArgumentException("Flag options cannot contain duplicates");
            }
        }

        @Override
        public boolean isValid(String value) {
            return options.contains(value);
        }

        @Override
        public List<String> tabComplete(String input) {
            return options.stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof OptionsFlagValueType other)) {
                return false;
            }
            return Set.copyOf(options).equals(Set.copyOf(other.options));
        }

        @Override
        public int hashCode() {
            return Set.copyOf(options).hashCode();
        }
    }
}
