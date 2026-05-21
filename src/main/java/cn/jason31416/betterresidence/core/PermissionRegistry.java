package cn.jason31416.betterresidence.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central registry for claim permission ids.
 * <p>
 * Other plugins may register their own permission ids here so BetterResidence can validate
 * {@code /res set} input and provide target-aware tab completion.
 */
public final class PermissionRegistry {
    private static final Map<String, RegisteredPermission> registry = new LinkedHashMap<>();

    private PermissionRegistry() {
    }

    /**
     * Register or replace a permission id.
     *
     * @param id The permission id, such as {@code block.break} or {@code quickshop.create}.
     * @param targetType The target type accepted by the optional {@code :target} suffix.
     */
    public static void registerPermission(String id, PermissionTargetType targetType) {
        registry.put(id, new RegisteredPermission(id, targetType));
    }

    /**
     * @return All registered permissions in registration order.
     */
    public static Collection<RegisteredPermission> getPermissions() {
        return registry.values();
    }

    /**
     * @return All registered permission ids in registration order.
     */
    public static List<String> getPermissionIds() {
        return List.copyOf(registry.keySet());
    }

    /**
     * Look up a registered permission by exact id.
     *
     * @param id The exact permission id without a {@code :target} suffix.
     * @return The registered permission, or empty if unknown.
     */
    public static Optional<RegisteredPermission> getPermission(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    /**
     * @param id The exact permission id without a {@code :target} suffix.
     * @return Whether the permission id is registered.
     */
    public static boolean isRegistered(String id) {
        return registry.containsKey(id);
    }

    /**
     * Return registered permission ids plus inferred parent hierarchy ids.
     * <p>
     * For example, {@code admin.area.add} contributes {@code admin} and {@code admin.area}.
     *
     * @return Permission ids that can be used in claim permission rules.
     */
    public static List<String> getHierarchyPermissionIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String permission : registry.keySet()) {
            ids.add(permission);
            ids.addAll(getParentPermissionIds(permission));
        }
        return List.copyOf(ids);
    }

    /**
     * Return inferred parent permission ids from nearest parent to root.
     *
     * @param permission The permission id, such as {@code a.b.c}.
     * @return Parent ids, such as {@code a.b}, then {@code a}.
     */
    public static List<String> getParentPermissionIds(String permission) {
        List<String> parents = new java.util.ArrayList<>();
        int separator = permission.lastIndexOf('.');
        while (separator >= 0) {
            String parent = permission.substring(0, separator);
            parents.add(parent);
            separator = parent.lastIndexOf('.');
        }
        return List.copyOf(parents);
    }

    /**
     * @param id The permission id without a {@code :target} suffix.
     * @return Whether the permission id is registered or inferred as a parent hierarchy id.
     */
    public static boolean isHierarchyPermission(String id) {
        return getHierarchyPermissionIds().contains(id);
    }

    /**
     * Resolve the target type for a permission hierarchy id.
     * <p>
     * Parent permission ids can only use targets when every registered descendant uses the same
     * target type.
     *
     * @param id The permission id without a {@code :target} suffix.
     * @return The target type, or empty if the id is unknown or has mixed descendant target types.
     */
    public static Optional<PermissionTargetType> getHierarchyTargetType(String id) {
        RegisteredPermission registeredPermission = registry.get(id);
        if (registeredPermission != null) {
            return Optional.of(registeredPermission.targetType());
        }

        Set<PermissionTargetType> targetTypes = registry.values().stream()
                .filter(permission -> permission.id().startsWith(id + "."))
                .map(RegisteredPermission::targetType)
                .collect(Collectors.toSet());
        if (targetTypes.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(targetTypes.iterator().next());
    }

    static {
        registerPermission("admin.trust", PermissionTargetType.NONE);
        registerPermission("admin.setpermission", PermissionTargetType.NONE);
        registerPermission("admin.setflag", PermissionTargetType.NONE);
        registerPermission("admin.removeclaim", PermissionTargetType.NONE);
        registerPermission("admin.subclaim", PermissionTargetType.NONE);
        registerPermission("admin.area.add", PermissionTargetType.NONE);
        registerPermission("admin.area.remove", PermissionTargetType.NONE);
        registerPermission("admin.area.list", PermissionTargetType.NONE);
        registerPermission("admin.area.resize", PermissionTargetType.NONE);

        registerPermission("block.break", PermissionTargetType.BLOCK);
        registerPermission("block.place", PermissionTargetType.BLOCK);
        registerPermission("block.interact", PermissionTargetType.BLOCK);
        registerPermission("use", PermissionTargetType.MATERIAL);
        registerPermission("dropitem.throw", PermissionTargetType.MATERIAL);
        registerPermission("dropitem.pickup", PermissionTargetType.MATERIAL);
        registerPermission("teleport", PermissionTargetType.NONE);
        registerPermission("entity.damage", PermissionTargetType.ENTITY);
        registerPermission("entity.interact", PermissionTargetType.ENTITY);
        registerPermission("entity.spawn", PermissionTargetType.ENTITY);
        registerPermission("enter", PermissionTargetType.NONE);
        registerPermission("fly", PermissionTargetType.NONE);
    }

    /**
     * A registered claim permission and the target type its optional suffix accepts.
     *
     * @param id The exact permission id.
     * @param targetType The target type for {@code :target} suffixes.
     */
    public record RegisteredPermission(String id, PermissionTargetType targetType) { }
}
