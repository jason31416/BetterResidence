package cn.jason31416.betterresidence.core;

import cn.jason31416.betterresidence.misc.IllegalConfigurationException;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.MapTree;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and validates globally configured claim permission groups.
 * <p>
 * Visitor/everyone are intentionally not stored here: visitor is the absence of a player group,
 * and everyone is only a permission threshold alias used by /res set.
 */
public final class DefaultClaimGroupRegistry {
    public static final int MIN_WEIGHT = -1000;
    public static final int MAX_WEIGHT = 1000;
    public static final int VISITOR_WEIGHT = 0;
    public static final int EVERYONE_WEIGHT = MIN_WEIGHT;

    public static final String OWNER_ID = "owner";
    public static final String TRUSTED_ID = "trusted";

    private static final String GROUPS_PATH = "claim.groups";
    private static final String LANG_PREFIX = "lang:";

    private static final Map<String, ClaimGroup> groupsById = new LinkedHashMap<>();
    private static final Map<String, ClaimGroup> groupsByName = new LinkedHashMap<>();

    private DefaultClaimGroupRegistry() {
    }

    /**
     * Reload configured groups from config.yml and fail fast on ambiguous or unsafe settings.
     */
    public static void loadConfig() {
        groupsById.clear();
        groupsByName.clear();

        if (!Config.contains(GROUPS_PATH)) {
            throw new IllegalConfigurationException("Missing required claim.groups section");
        }

        MapTree groups = Config.getSection(GROUPS_PATH);
        for (String groupId : groups.getKeys()) {
            String path = groupId + ".";
            String rawName = groups.getString(path + "name", null);
            if (rawName == null || rawName.isBlank()) {
                throw new IllegalConfigurationException("Missing claim.groups." + groupId + ".name");
            }

            if (!groups.contains(path + "weight")) {
                throw new IllegalConfigurationException("Missing claim.groups." + groupId + ".weight");
            }

            String name = resolveConfiguredName(rawName);
            if (name.isBlank()) {
                throw new IllegalConfigurationException("Resolved claim group name cannot be empty: " + groupId);
            }

            int weight = groups.getInt(path + "weight");
            validateWeight("claim.groups." + groupId + ".weight", weight);

            if (name.equals(getVisitorName()) || name.equals(getEveryoneName())) {
                throw new IllegalConfigurationException("Configured claim group name cannot use reserved name: " + name);
            }
            if (groupsById.containsKey(groupId)) {
                throw new IllegalConfigurationException("Duplicate configured claim group id: " + groupId);
            }
            if (groupsByName.containsKey(name)) {
                throw new IllegalConfigurationException("Duplicate configured claim group name: " + name);
            }

            ClaimGroup group = new ClaimGroup(groupId, name, weight);
            groupsById.put(groupId, group);
            groupsByName.put(name, group);
        }

        ClaimGroup owner = groupsById.get(OWNER_ID);
        if (owner == null) {
            throw new IllegalConfigurationException("Missing required claim.groups.owner");
        }
        if (owner.weight() != MAX_WEIGHT) {
            throw new IllegalConfigurationException("claim.groups.owner.weight must be exactly 1000");
        }
        if (!groupsById.containsKey(TRUSTED_ID)) {
            throw new IllegalConfigurationException("Missing required claim.groups.trusted");
        }
    }

    public static ClaimGroup getOwnerGroup() {
        return getRequiredGroup(OWNER_ID);
    }

    public static ClaimGroup getTrustedGroup() {
        return getRequiredGroup(TRUSTED_ID);
    }

    public static Collection<ClaimGroup> getConfiguredGroups() {
        return groupsById.values();
    }

    public static String getVisitorName() {
        return Lang.messageLoader.getRawMessage("claim.group.none", "Visitor");
    }

    public static String getEveryoneName() {
        return Lang.messageLoader.getRawMessage("claim.group.everyone", "Everyone");
    }

    public static void validateWeight(String source, int weight) {
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            throw new IllegalConfigurationException(source + " must be between -1000 and 1000");
        }
    }

    private static ClaimGroup getRequiredGroup(String groupId) {
        ClaimGroup group = groupsById.get(groupId);
        if (group == null) {
            throw new IllegalStateException("Claim groups have not been loaded: " + groupId);
        }
        return group;
    }

    private static String resolveConfiguredName(String rawName) {
        if (!rawName.startsWith(LANG_PREFIX)) {
            return rawName;
        }

        String key = rawName.substring(LANG_PREFIX.length());
        return Lang.messageLoader.getRawMessage(key, key);
    }
}
