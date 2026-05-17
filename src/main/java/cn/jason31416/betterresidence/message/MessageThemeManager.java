package cn.jason31416.betterresidence.message;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.betterresidence.misc.IllegalConfigurationException;
import cn.jason31416.planetlib.message.InternalPlaceholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MessageThemeManager {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private static Map<String, Map<String, String>> themes = Map.of();
    private static Map<String, String> activeTheme = Map.of();

    private MessageThemeManager() {
    }

    public static void loadThemesFromFile(String relativePath) {
        ensureRegistered();

        File file = new File(BetterResidence.getInstance().getDataFolder(), relativePath);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, Map<String, String>> loadedThemes = new LinkedHashMap<>();

        for (String themeName : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(themeName);
            if (section == null) {
                throw new IllegalConfigurationException("Theme '" + themeName + "' must be a section");
            }

            Map<String, String> themeValues = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value == null || value.isBlank()) {
                    throw new IllegalConfigurationException("Theme '" + themeName + "' key '" + key + "' cannot be empty");
                }
                themeValues.put(key, value);
            }

            loadedThemes.put(themeName, Map.copyOf(themeValues));
        }

        if (loadedThemes.isEmpty()) {
            throw new IllegalConfigurationException("No themes found in " + relativePath);
        }

        themes = Map.copyOf(loadedThemes);
    }

    public static void useTheme(String name) {
        Map<String, String> theme = themes.get(name);
        if (theme == null) {
            throw new IllegalConfigurationException("Unknown theme '" + name + "'");
        }
        activeTheme = theme;
    }

    private static void ensureRegistered() {
        if (REGISTERED.compareAndSet(false, true)) {
            InternalPlaceholder.registerPlaceholderHandler((message, player) -> applyTheme(message));
        }
    }

    private static String applyTheme(String message) {
        String themedMessage = message;
        for (Map.Entry<String, String> entry : activeTheme.entrySet()) {
            themedMessage = themedMessage.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return themedMessage;
    }
}
