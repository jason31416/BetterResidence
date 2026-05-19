package cn.jason31416.betterresidence.core;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Describes the optional {@code :target} suffix accepted by a permission.
 * <p>
 * Built-in instances cover common targets, but other plugins may create their own instances and
 * register permissions with {@link PermissionRegistry#registerPermission(String, PermissionTargetType)}.
 */
public final class PermissionTargetType {
    /** Permission type that does not accept any {@code :target} suffix. */
    public static final PermissionTargetType NONE = new PermissionTargetType(
            "none",
            prefix -> List.of(),
            (targetType, ruleTarget, checkedTarget) -> ruleTarget == null ? 0 : -1
    );
    /** Permission type targeting block materials only. Tab completion only includes {@link Material#isBlock()} materials. */
    public static final PermissionTargetType BLOCK = new PermissionTargetType(
            "block",
            prefix -> completeMaterial(prefix, true),
            PermissionTargetType::resolveGroupedTargetPriority
    );
    /** Permission type targeting any Bukkit material, including items and blocks. */
    public static final PermissionTargetType MATERIAL = new PermissionTargetType(
            "material",
            prefix -> completeMaterial(prefix, false),
            PermissionTargetType::resolveGroupedTargetPriority
    );
    /** Permission type targeting Bukkit entity types. */
    public static final PermissionTargetType ENTITY = new PermissionTargetType(
            "entity",
            PermissionTargetType::completeEntity,
            PermissionTargetType::resolveGroupedTargetPriority
    );

    private final String id;
    private final Function<String, List<String>> tabCompleter;
    private final TargetPriorityResolver priorityResolver;

    /**
     * Create a target type for custom permission targets.
     *
     * @param id Stable id used by target group configuration sections.
     * @param tabCompleter Returns suffix candidates for a non-empty prefix.
     * @param priorityResolver Resolves whether a stored rule target matches a checked target.
     */
    public PermissionTargetType(String id, Function<String, List<String>> tabCompleter, TargetPriorityResolver priorityResolver) {
        this.id = id;
        this.tabCompleter = tabCompleter;
        this.priorityResolver = priorityResolver;
    }

    /**
     * @return Stable id used in {@code target-groups.yml}.
     */
    public String getId() {
        return id;
    }

    /**
     * Complete a target suffix for this type.
     *
     * @param prefix Already typed suffix text after {@code :}. Empty prefixes intentionally return no candidates.
     * @return Matching suffix candidates without the permission prefix.
     */
    public List<String> tabComplete(String prefix) {
        if (prefix.isEmpty()) {
            return List.of();
        }
        return tabCompleter.apply(prefix);
    }

    /**
     * Resolve match priority between a rule target and the target being checked.
     *
     * @param ruleTarget The suffix stored in a permission rule, or null for an untargeted rule.
     * @param checkedTarget The runtime target, such as a material or entity type, or null.
     * @return {@code -1} when not matched; otherwise a priority where larger values are more specific.
     */
    public int getTargetPriority(@Nullable String ruleTarget, @Nullable String checkedTarget) {
        return priorityResolver.getPriority(this, ruleTarget, checkedTarget);
    }

    private static List<String> completeMaterial(String prefix, boolean blocksOnly) {
        List<String> result = new ArrayList<>();
        PermissionTargetType targetType = blocksOnly ? BLOCK : MATERIAL;
        result.addAll(TargetGroup.getTargetGroupIds(targetType));
        for (Material material : Material.values()) {
            if (!blocksOnly || material.isBlock()) {
                result.add(material.name().toLowerCase());
            }
        }
        return result.stream()
                .distinct()
                .filter(target -> target.startsWith(prefix))
                .toList();
    }

    private static List<String> completeEntity(String prefix) {
        List<String> result = new ArrayList<>();
        result.addAll(TargetGroup.getTargetGroupIds(ENTITY));
        for (EntityType entityType : EntityType.values()) {
            result.add(entityType.name().toLowerCase());
        }
        return result.stream()
                .distinct()
                .filter(target -> target.startsWith(prefix))
                .toList();
    }

    private static int resolveGroupedTargetPriority(PermissionTargetType targetType, @Nullable String ruleTarget, @Nullable String checkedTarget) {
        if (ruleTarget == null) {
            return 0;
        }
        if (checkedTarget == null) {
            return -1;
        }

        String normalizedTarget = checkedTarget.toLowerCase();
        if (ruleTarget.equals(normalizedTarget)) {
            return Integer.MAX_VALUE;
        }

        TargetGroup group = TargetGroup.getTargetGroup(targetType, ruleTarget);
        if (group == null || !group.isInGroup(normalizedTarget)) {
            return -1;
        }
        return group.getPriority();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PermissionTargetType that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @FunctionalInterface
    public interface TargetPriorityResolver {
        /**
         * Resolve match priority for a target type.
         *
         * @param targetType The target type owning this resolver.
         * @param ruleTarget The suffix stored in a permission rule, or null.
         * @param checkedTarget The runtime target, or null.
         * @return {@code -1} when not matched; otherwise a priority where larger values are more specific.
         */
        int getPriority(PermissionTargetType targetType, @Nullable String ruleTarget, @Nullable String checkedTarget);
    }
}
