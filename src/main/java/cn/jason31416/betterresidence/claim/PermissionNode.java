package cn.jason31416.betterresidence.claim;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Locale;

@Getter @AllArgsConstructor
public class PermissionNode {
    private final String claimUUID;
    private final String name;
    private final String material; // can be a direct Material or a material group's id. Consider material only for dev.
    private final int weight; // The node will only apply to group weights greater than or equal to this value
    private final boolean state;

    public String getPermissionKey(){
        if(material==null){
            return name;
        }
        return name+":"+material;
    }

    public int getPermissionPriority(String permission) {
        // Exact permission nodes beat wildcard nodes, regardless of material specificity.
        if (name.equals(permission)) {
            return 2;
        }

        // Global wildcard applies to every permission, but has the lowest name priority.
        if (name.equals("*")) {
            return 0;
        }

        // Category wildcards such as block.* match block.break, block.place, etc.
        if (name.endsWith(".*")) {
            String prefix = name.substring(0, name.length() - 1);
            if (permission.startsWith(prefix)) {
                return 1;
            }
        }

        return -1; // This node doesn't match the permission
    }

    public int getMaterialPriority(@Nullable String checkedMaterial) {
        if (checkedMaterial == null || material == null) {
            // when checkedMaterial or material is null, its either the event don't have material or this node doesn't care about material, we should skip this check.
            return 0;
        }

        String normalizedMaterial = checkedMaterial.toLowerCase(Locale.ROOT);
        if (material.equals(normalizedMaterial)) {
            return Integer.MAX_VALUE;
        }

        // Material groups keep their existing priority ranking within the same permission name priority.
        MaterialGroup group = MaterialGroup.getMaterialGroup(material);
        if (group == null || !group.isInGroup(normalizedMaterial)) {
            return -1; // Material doesn't match, so this permission isn't applicable
        }
        return group.getPriority();
    }
}
