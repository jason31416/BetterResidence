package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.PermissionRegistry;
import cn.jason31416.betterresidence.core.PermissionTargetType;
import cn.jason31416.betterresidence.core.TargetGroup;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class PermissionCommandFormat {
    private PermissionCommandFormat() {
    }

    static List<String> tabCompletePermission(String input) {
        int targetSeparator = input.indexOf(':');
        if (targetSeparator >= 0) {
            String permission = input.substring(0, targetSeparator);
            String targetPrefix = input.substring(targetSeparator + 1);
            return tabCompleteTarget(permission, targetPrefix);
        }

        return PermissionRegistry.getHierarchyPermissionIds().stream()
                .filter(permission -> permission.startsWith(input))
                .filter(permission -> shouldShowPermissionCompletion(input, permission))
                .toList();
    }

    static Optional<PermissionTargetType> getTargetType(String permission) {
        String basePermission = getBasePermission(permission);
        if (basePermission.equals("*")) {
            return Optional.of(permission.contains(":") ? PermissionTargetType.BLOCK : PermissionTargetType.NONE);
        }

        Optional<PermissionTargetType> targetType = PermissionRegistry.getHierarchyTargetType(basePermission);
        if (targetType.isPresent() || permission.contains(":")) {
            return targetType;
        }
        if (PermissionRegistry.isHierarchyPermission(basePermission)) {
            return Optional.of(PermissionTargetType.NONE);
        }
        return Optional.empty();
    }

    static String getBasePermission(String permission) {
        int targetSeparator = permission.indexOf(':');
        if (targetSeparator >= 0) {
            return permission.substring(0, targetSeparator);
        }
        return permission;
    }

    static @Nullable String getTarget(String permission) {
        int targetSeparator = permission.indexOf(':');
        if (targetSeparator >= 0) {
            return permission.substring(targetSeparator + 1);
        }
        return null;
    }

    static String displayPermission(String permission, PermissionTargetType targetType) {
        String basePermission = getBasePermission(permission);
        String target = getTarget(permission);
        String material = target == null ? "" : displayTarget(targetType, target);
        String action = Lang.getMessage("claim.action." + basePermission, basePermission)
                .add("material", material)
                .toFormatted();
        return Message.of("%action% <muted>(%permission%)")
                .add("action", action)
                .add("permission", ClaimCommandFormat.escape(permission))
                .toFormatted();
    }

    private static boolean shouldShowPermissionCompletion(String input, String permission) {
        return input.chars().filter(c -> c == '.').count() == permission.chars().filter(c -> c == '.').count()
                && (!permission.contains(".") || input.startsWith(permission.substring(0, permission.lastIndexOf('.'))));
    }

    private static List<String> tabCompleteTarget(String permission, String targetPrefix) {
        if (targetPrefix.isEmpty()) {
            return List.of();
        }

        Optional<PermissionTargetType> targetType = getTargetType(permission);
        if (targetType.isEmpty()) {
            return List.of();
        }

        return targetType.get().tabComplete(targetPrefix).stream()
                .map(target -> permission + ":" + target)
                .toList();
    }

    private static String displayTarget(PermissionTargetType targetType, String target) {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        if (TargetGroup.getTargetGroup(targetType, normalizedTarget) != null) {
            return ClaimCommandFormat.rawMessage("target-group." + targetType.getId() + "." + normalizedTarget)
                    .toFormatted();
        }

        if (targetType.equals(PermissionTargetType.BLOCK) || targetType.equals(PermissionTargetType.MATERIAL)) {
            Material material = Material.getMaterial(target.toUpperCase(Locale.ROOT));
            if (material != null) {
                return "<lang:" + material.translationKey() + ">";
            }
        }

        if (targetType.equals(PermissionTargetType.ENTITY)) {
            try {
                EntityType entityType = EntityType.valueOf(target.toUpperCase(Locale.ROOT));
                return "<lang:" + entityType.translationKey() + ">";
            } catch (IllegalArgumentException ignored) {
            }
        }

        return ClaimCommandFormat.escape(normalizedTarget);
    }
}
