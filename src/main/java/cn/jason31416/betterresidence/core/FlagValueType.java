package cn.jason31416.betterresidence.core;

import java.util.List;
import java.util.Objects;

public interface FlagValueType {
    boolean isValid(String value);

    default List<String> tabComplete(String input) {
        return List.of();
    }

    static FlagValueType string() {
        return Objects::nonNull;
    }

    static FlagValueType integer() {
        return value -> {
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        };
    }

    static FlagValueType decimal() {
        return value -> {
            try {
                Double.parseDouble(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        };
    }

    static FlagValueType bool() {
        return options("true", "false");
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
    }
}
