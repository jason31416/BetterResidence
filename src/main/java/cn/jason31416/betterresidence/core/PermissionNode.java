package cn.jason31416.betterresidence.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.annotation.Nullable;

@Getter @AllArgsConstructor
/**
 * A single permission threshold rule for a claim.
 * <p>
 * The node stores the permission name, optional target suffix, and minimum group weight required
 * to satisfy the rule.
 */
public class PermissionNode {
    private final String claimUUID;
    private final String name;
    private final String target; // can be a direct target or a target group's id.
    private final int weight; // Minimum group weight required for this permission.

    /**
     * @return The full permission key, including {@code :target} when present.
     */
    public String getPermissionKey(){
        if(target==null){
            return name;
        }
        return name+":"+target;
    }

    /**
     * Resolve how specifically this node's permission name matches a checked permission.
     *
     * @param permission Runtime permission id without {@code :target}.
     * @return {@code -1} when not matched; otherwise a priority where larger values are more specific.
     */
    public int getPermissionPriority(String permission) {
        // Exact permission nodes beat wildcard nodes, regardless of material specificity.
        if (name.equals(permission)) {
            return Integer.MAX_VALUE;
        }

        // Global wildcard applies to every permission, but has the lowest name priority.
        if (name.equals("*")) {
            return 0;
        }

        if (PermissionRegistry.getParentPermissionIds(permission).contains(name)) {
            return 1 + name.length();
        }

        return -1; // This node doesn't match the permission
    }

    /**
     * Resolve how specifically this node's optional target matches a checked runtime target.
     *
     * @param targetType Target type of the checked permission.
     * @param checkedTarget Runtime target id, or null for untargeted checks.
     * @return {@code -1} when not matched; otherwise a priority where larger values are more specific.
     */
    public int getTargetPriority(PermissionTargetType targetType, @Nullable String checkedTarget) {
        return targetType.getTargetPriority(target, checkedTarget);
    }
}
