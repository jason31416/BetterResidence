package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;
import cn.jason31416.planetlib.util.MapTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerGroupReferenceChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "player-group-references";
    }

    @Override
    public String description() {
        return "Player group assignments must reference assignable groups for their claim and must not include owners or duplicate rows.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        Map<String, Set<String>> groupIdsByClaim = fetchGroupIdsByClaim();
        Map<String, String> ownerByClaim = fetchOwnerByClaim();
        Set<String> seenAssignments = new HashSet<>();
        List<Map<String, Object>> corruptedRows = new ArrayList<>();

        for (MapTree row : DataHandler.getDatabase().select("player_groups").list()) {
            String claimUuid = row.getString("claim_uuid", null);
            String playerUuid = row.getString("player_uuid", null);
            String groupId = row.getString("group_id", null);
            String error = validateAssignment(groupIdsByClaim, ownerByClaim, seenAssignments, claimUuid, playerUuid, groupId);
            if (error == null) {
                continue;
            }
            Map<String, Object> corruptedRow = new LinkedHashMap<>();
            corruptedRow.put("claim_uuid", claimUuid);
            corruptedRow.put("player_uuid", playerUuid);
            corruptedRow.put("group_id", groupId);
            corruptedRow.put("integrity_error", error);
            corruptedRows.add(corruptedRow);
        }
        return corruptedRows;
    }

    private Map<String, Set<String>> fetchGroupIdsByClaim() {
        Map<String, Set<String>> result = new HashMap<>();
        Set<String> configuredGroupIds = new HashSet<>();
        DefaultClaimGroupRegistry.getConfiguredGroups().forEach(group -> {
            if (!group.id().equals(DefaultClaimGroupRegistry.OWNER_ID)) {
                configuredGroupIds.add(group.id());
            }
        });
        for (MapTree claim : DataHandler.getDatabase().select("claim").list()) {
            result.put(claim.getString("uuid"), new HashSet<>(configuredGroupIds));
        }
        for (MapTree group : DataHandler.getDatabase().select("group_weights").list()) {
            result.computeIfAbsent(group.getString("claim_uuid", null), ignored -> new HashSet<>())
                    .add(group.getString("group_id", null));
        }
        return result;
    }

    private Map<String, String> fetchOwnerByClaim() {
        Map<String, String> result = new HashMap<>();
        for (MapTree claim : DataHandler.getDatabase().select("claim").list()) {
            result.put(claim.getString("uuid", null), claim.getString("owner_uuid", null));
        }
        return result;
    }

    private String validateAssignment(Map<String, Set<String>> groupIdsByClaim, Map<String, String> ownerByClaim,
                                      Set<String> seenAssignments, String claimUuid, String playerUuid, String groupId) {
        String assignmentKey = claimUuid + "\u0000" + playerUuid;
        if (!seenAssignments.add(assignmentKey)) {
            return "duplicate player group assignment for claim and player";
        }
        if (playerUuid != null && playerUuid.equals(ownerByClaim.get(claimUuid))) {
            return "claim owner must not be stored in player_groups";
        }
        if (groupId == null || groupId.isBlank()) {
            return "group_id is blank";
        }
        if (groupId.equals(DefaultClaimGroupRegistry.OWNER_ID)) {
            return "owner group is not assignable";
        }
        if (!groupIdsByClaim.getOrDefault(claimUuid, Set.of()).contains(groupId)) {
            return "group_id does not exist for this claim";
        }
        return null;
    }
}
