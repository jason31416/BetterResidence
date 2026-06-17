package cn.jason31416.betterresidence.core;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.betterresidence.misc.IllegalConfigurationException;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.MapTree;
import cn.jason31416.planetlib.util.Util;
import lombok.Getter;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Named group of permission targets for one {@link PermissionTargetType}.
 * <p>
 * Groups allow rules like {@code block.break:container}, where {@code container} maps to one or
 * more concrete runtime targets such as {@code chest}. Groups are loaded from {@code target-groups.yml}.
 */
public final class TargetGroup {
    @Getter
    private static final Map<PermissionTargetType, Map<String, TargetGroup>> targetGroups = new ConcurrentHashMap<>();

    private final PermissionTargetType targetType;
    @Getter
    private final String id;
    private final String name;
    @Getter
    private final int priority;
    private final List<String> patterns;
    private final List<Pattern> compiledPatterns;

    /**
     * Create a target group.
     *
     * @param targetType The target type this group belongs to.
     * @param id The group id used in permission suffixes.
     * @param name Translation key or display name.
     * @param priority Group specificity priority; larger values beat broader groups.
     * @param patterns Case-insensitive regex patterns matched against runtime target ids.
     */
    public TargetGroup(PermissionTargetType targetType, String id, String name, int priority, List<String> patterns) {
        this.targetType = targetType;
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.patterns = patterns;
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(Pattern.compile(pattern.toLowerCase(), Pattern.CASE_INSENSITIVE));
        }
        this.compiledPatterns = List.copyOf(compiled);
    }

    /**
     * @return Localized display name for this group.
     */
    public String getName() {
        return Lang.messageLoader.getRawMessage(name, name);
    }

    /**
     * @param target Runtime target id, usually lower-case material or entity type name.
     * @return Whether the target matches any configured pattern.
     */
    public boolean isInGroup(String target) {
        String lowerTarget = target.toLowerCase();
        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(lowerTarget).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Look up a target group by type and id.
     *
     * @param targetType The target type namespace.
     * @param id The group id used in permission suffixes.
     * @return The target group, or null if not found.
     */
    public static TargetGroup getTargetGroup(PermissionTargetType targetType, String id) {
        return targetGroups.getOrDefault(targetType, Map.of()).get(id);
    }

    /**
     * @param targetType The target type namespace.
     * @return All group ids registered for the target type.
     */
    public static Set<String> getTargetGroupIds(PermissionTargetType targetType) {
        return Set.copyOf(targetGroups.getOrDefault(targetType, Map.of()).keySet());
    }

    /**
     * Reload target groups from {@code target-groups.yml}.
     */
    @SneakyThrows
    public static void loadConfig() {
        targetGroups.clear();

        Util.savePluginResource("target-groups.yml");
        MapTree mapTree = MapTree.fromYaml(Files.readString(BetterResidence.getInstance().getDataFolder().toPath().resolve("target-groups.yml")));
        registerGroups(mapTree, PermissionTargetType.BLOCK);
        registerGroups(mapTree, PermissionTargetType.MATERIAL);
        registerGroups(mapTree, PermissionTargetType.ENTITY);
    }

    private static void registerGroups(MapTree mapTree, PermissionTargetType targetType) {
        String section = targetType.getId();
        if (!mapTree.contains(section)) {
            return;
        }

        Map<String, TargetGroup> groups = new HashMap<>();
        MapTree targetSection = mapTree.getSection(section);
        if (!targetSection.contains("all")) {
            throw new IllegalConfigurationException("Missing `" + section + ".all` target group");
        }
        for (String key : targetSection.getKeys()) {
            groups.put(key, new TargetGroup(
                    targetType,
                    key,
                    targetSection.getString(key + ".name"),
                    targetSection.getInt(key + ".priority"),
                    targetSection.getStringList(key + ".targets")
            ));
        }
        targetGroups.put(targetType, groups);
    }
}
