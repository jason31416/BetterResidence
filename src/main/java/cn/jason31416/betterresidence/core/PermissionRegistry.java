package cn.jason31416.betterresidence.core;

import java.util.Collection;
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
     * Return top-level namespaces inferred from registered dotted permission ids.
     * <p>
     * For example, {@code block.break} contributes {@code block}.
     *
     * @return Registered permission namespaces.
     */
    public static Set<String> getNamespaces() {
        return registry.keySet().stream()
                .filter(permission -> permission.contains("."))
                .map(permission -> permission.substring(0, permission.indexOf('.')))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolve the target type for a wildcard namespace such as {@code block.*}.
     * <p>
     * A namespace target type can only be inferred when every registered permission under the
     * namespace uses the same target type.
     *
     * @param namespace The namespace without trailing {@code .*}.
     * @return The shared target type, or empty if the namespace is unknown or mixed.
     */
    public static Optional<PermissionTargetType> getNamespaceTargetType(String namespace) {
        Set<PermissionTargetType> targetTypes = registry.values().stream()
                .filter(permission -> permission.id().startsWith(namespace + "."))
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

        registerPermission("block.break", PermissionTargetType.BLOCK);
        registerPermission("block.place", PermissionTargetType.BLOCK);
        registerPermission("block.interact", PermissionTargetType.BLOCK);
        registerPermission("entity.damage", PermissionTargetType.ENTITY);
        registerPermission("entity.interact", PermissionTargetType.ENTITY);
        registerPermission("entity.spawn", PermissionTargetType.ENTITY);
        registerPermission("enter", PermissionTargetType.NONE);
    }

    /**
     * A registered claim permission and the target type its optional suffix accepts.
     *
     * @param id The exact permission id.
     * @param targetType The target type for {@code :target} suffixes.
     */
    public record RegisteredPermission(String id, PermissionTargetType targetType) { }
}
