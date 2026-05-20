package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.core.PermissionRegistry;
import cn.jason31416.betterresidence.core.PermissionTargetType;
import cn.jason31416.betterresidence.core.TargetGroup;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;
import cn.jason31416.planetlib.util.MapTree;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PermissionRuleIntegrityChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "permission-rule-integrity";
    }

    @Override
    public String description() {
        return "Stored permission rules must be valid, unique per claim, and compatible with registered permission target types.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        Set<String> seenRules = new HashSet<>();
        List<Map<String, Object>> corruptedRows = new ArrayList<>();
        for (MapTree row : DataHandler.getDatabase().select("claim_permissions").list()) {
            String claimUuid = row.getString("claim_uuid", null);
            String permission = row.getString("permission", null);
            int weight = row.getInt("weight");
            String error = validateRule(seenRules, claimUuid, permission, weight);
            if (error == null) {
                continue;
            }
            Map<String, Object> corruptedRow = new LinkedHashMap<>();
            corruptedRow.put("claim_uuid", claimUuid);
            corruptedRow.put("permission", permission);
            corruptedRow.put("weight", weight);
            corruptedRow.put("integrity_error", error);
            corruptedRows.add(corruptedRow);
        }
        return corruptedRows;
    }

    private String validateRule(Set<String> seenRules, String claimUuid, String permission, int weight) {
        if (permission == null || permission.isBlank()) {
            return "permission is blank";
        }
        if (weight < DefaultClaimGroupRegistry.MIN_WEIGHT || weight > DefaultClaimGroupRegistry.MAX_WEIGHT) {
            return "weight is outside -1000..1000";
        }
        if (!seenRules.add(claimUuid + "\u0000" + permission)) {
            return "duplicate permission rule in claim";
        }
        int separator = permission.indexOf(':');
        if (separator != permission.lastIndexOf(':')) {
            return "permission has more than one target separator";
        }
        String basePermission = separator >= 0 ? permission.substring(0, separator) : permission;
        String target = separator >= 0 ? permission.substring(separator + 1) : null;
        if (basePermission.isBlank()) {
            return "permission base is blank";
        }
        if (separator >= 0 && target.isBlank()) {
            return "permission target is blank";
        }

        Optional<PermissionTargetType> targetType = getTargetType(basePermission);
        if (targetType.isEmpty()) {
            if (target == null && PermissionRegistry.isHierarchyPermission(basePermission)) {
                return null;
            }
            return "permission base is not registered or does not support targets";
        }
        if (target != null && targetType.get().equals(PermissionTargetType.NONE)) {
            return "permission does not support targets";
        }
        if (target != null && !isValidTarget(targetType.get(), target)) {
            return "permission target is not a known concrete target or target group";
        }
        return null;
    }

    private Optional<PermissionTargetType> getTargetType(String basePermission) {
        if (basePermission.equals("*")) {
            return Optional.of(new PermissionTargetType(
                    "wildcard",
                    ignored -> List.of(),
                    (targetType, ruleTarget, checkedTarget) -> 0
            ));
        }
        return PermissionRegistry.getHierarchyTargetType(basePermission);
    }

    private boolean isValidTarget(PermissionTargetType targetType, String target) {
        String normalizedTarget = target.toLowerCase(java.util.Locale.ROOT);
        if (TargetGroup.getTargetGroup(targetType, normalizedTarget) != null) {
            return true;
        }
        if (targetType.getId().equals("wildcard")) {
            return isValidTarget(PermissionTargetType.BLOCK, target)
                    || isValidTarget(PermissionTargetType.MATERIAL, target)
                    || isValidTarget(PermissionTargetType.ENTITY, target);
        }
        if (targetType.equals(PermissionTargetType.BLOCK)) {
            Material material = Material.matchMaterial(normalizedTarget);
            return material != null && material.isBlock();
        }
        if (targetType.equals(PermissionTargetType.MATERIAL)) {
            return Material.matchMaterial(normalizedTarget) != null;
        }
        if (targetType.equals(PermissionTargetType.ENTITY)) {
            try {
                EntityType.valueOf(normalizedTarget.toUpperCase(java.util.Locale.ROOT));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }
}
