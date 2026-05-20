package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;
import cn.jason31416.planetlib.util.MapTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomGroupIntegrityChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "custom-group-integrity";
    }

    @Override
    public String description() {
        return "Per-claim custom groups must have valid weights and unambiguous ids/names.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        Set<String> configuredIds = new HashSet<>();
        Set<String> configuredNames = new HashSet<>();
        DefaultClaimGroupRegistry.getConfiguredGroups().forEach(group -> {
            configuredIds.add(group.id());
            configuredNames.add(group.name());
        });

        Set<String> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        List<Map<String, Object>> corruptedRows = new ArrayList<>();
        for (MapTree row : DataHandler.getDatabase().select("group_weights").list()) {
            String claimUuid = row.getString("claim_uuid", null);
            String groupId = row.getString("group_id", null);
            String groupName = row.getString("group_name", null);
            int weight = row.getInt("weight");
            String error = validateGroup(configuredIds, configuredNames, seenIds, seenNames, claimUuid, groupId, groupName, weight);
            if (error == null) {
                continue;
            }
            Map<String, Object> corruptedRow = new LinkedHashMap<>();
            corruptedRow.put("claim_uuid", claimUuid);
            corruptedRow.put("group_id", groupId);
            corruptedRow.put("group_name", groupName);
            corruptedRow.put("weight", weight);
            corruptedRow.put("integrity_error", error);
            corruptedRows.add(corruptedRow);
        }
        return corruptedRows;
    }

    private String validateGroup(Set<String> configuredIds, Set<String> configuredNames, Set<String> seenIds,
                                 Set<String> seenNames, String claimUuid, String groupId, String groupName, int weight) {
        if (groupId == null || groupId.isBlank()) {
            return "group_id is blank";
        }
        if (groupName == null || groupName.isBlank()) {
            return "group_name is blank";
        }
        if (weight < DefaultClaimGroupRegistry.MIN_WEIGHT || weight > DefaultClaimGroupRegistry.MAX_WEIGHT) {
            return "weight is outside -1000..1000";
        }
        if (configuredIds.contains(groupId)) {
            return "custom group_id shadows configured group id";
        }
        if (configuredNames.contains(groupName)) {
            return "custom group_name shadows configured group name";
        }
        if (groupName.equals(DefaultClaimGroupRegistry.getVisitorName()) || groupName.equals(DefaultClaimGroupRegistry.getEveryoneName())) {
            return "custom group_name uses a reserved alias";
        }
        if (!seenIds.add(claimUuid + "\u0000" + groupId)) {
            return "duplicate custom group_id in claim";
        }
        if (!seenNames.add(claimUuid + "\u0000" + groupName)) {
            return "duplicate custom group_name in claim";
        }
        return null;
    }
}
