package cn.jason31416.betterresidence.claim;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Just new an instance of this class
 * It will automatically register itself.
 */
@Getter
public final class PermissionType {
    // This registry is mainly for tab-complete in commands
    @Getter
    private static final List<PermissionType> registry = new ArrayList<>();

    private final String id;
    private final boolean haveMaterial; // Note that the material here refer to the section after the :
    public PermissionType(String id, boolean haveMaterial) {
        this.id = id;
        this.haveMaterial = haveMaterial;
        registry.add(this);
    }

    public static PermissionType BLOCK_INTERACT = new PermissionType("block.interact", true);
    public static PermissionType BLOCK_BREAK = new PermissionType("block.break", true);
    public static PermissionType BLOCK_PLACE = new PermissionType("block.place", true);
    public static PermissionType ENTITY_DAMAGE = new PermissionType("entity.damage", true);
    public static PermissionType ENTITY_INTERACT = new PermissionType("entity.interact", true);
}
