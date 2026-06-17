package cn.jason31416.betterresidence.core;

import cn.jason31416.planetlib.util.Config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ClaimNameValidator {
    public static final String CONFIG_PATH = "claim.name-regex";
    private static final String DEFAULT_REGEX = "[A-Za-z0-9_-]{1,64}";

    private static volatile Pattern cachedPattern;
    private static volatile String cachedRegex;

    private ClaimNameValidator() {
    }

    public static boolean isValid(String name) {
        if (name == null) {
            return false;
        }
        return getPattern().matcher(name).matches();
    }

    public static String getRegex() {
        return Config.getString(CONFIG_PATH, DEFAULT_REGEX);
    }

    public static void validateConfig() {
        cachedPattern = null;
        getPattern();
    }

    private static Pattern getPattern() {
        String regex = getRegex();
        Pattern cached = cachedPattern;
        if (cached != null && regex.equals(cachedRegex)) {
            return cached;
        }
        try {
            Pattern compiled = Pattern.compile(regex);
            cachedPattern = compiled;
            cachedRegex = regex;
            return compiled;
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid " + CONFIG_PATH + ": " + e.getMessage(), e);
        }
    }
}
