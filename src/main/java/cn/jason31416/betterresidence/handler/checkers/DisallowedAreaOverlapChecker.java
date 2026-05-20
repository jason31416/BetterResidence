package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DisallowedAreaOverlapChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "disallowed-area-overlap";
    }

    @Override
    public String description() {
        return "Claim areas may only overlap areas from their ancestor chain; self, sibling, descendant, and unrelated overlaps are invalid.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT a_link.area_id AS area_id,
                       a_link.claim_uuid AS claim_uuid,
                       a_claim.name AS claim_name,
                       a_claim.parent_uuid AS parent_uuid,
                       a_link.world AS world,
                       a.minX,
                       a.maxX,
                       a.minY,
                       a.maxY,
                       a.minZ,
                       a.maxZ,
                       b_link.area_id AS overlapping_area_id,
                       b_link.claim_uuid AS overlapping_claim_uuid,
                       b_claim.name AS overlapping_claim_name
                FROM claim_areas a_link
                JOIN area a ON a.id = a_link.area_id
                JOIN claim a_claim ON a_claim.uuid = a_link.claim_uuid
                JOIN claim_areas b_link ON b_link.world = a_link.world
                                      AND b_link.area_id > a_link.area_id
                JOIN area b ON b.id = b_link.area_id
                JOIN claim b_claim ON b_claim.uuid = b_link.claim_uuid
                WHERE a.minX <= b.maxX
                  AND a.maxX >= b.minX
                  AND a.minY <= b.maxY
                  AND a.maxY >= b.minY
                  AND a.minZ <= b.maxZ
                  AND a.maxZ >= b.minZ
                ORDER BY a_link.area_id ASC, b_link.area_id ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("area_id", rs.getObject("area_id"));
                    row.put("claim_uuid", rs.getString("claim_uuid"));
                    row.put("claim_name", rs.getString("claim_name"));
                    row.put("parent_uuid", rs.getString("parent_uuid"));
                    row.put("world", rs.getString("world"));
                    row.put("minX", rs.getObject("minX"));
                    row.put("maxX", rs.getObject("maxX"));
                    row.put("minY", rs.getObject("minY"));
                    row.put("maxY", rs.getObject("maxY"));
                    row.put("minZ", rs.getObject("minZ"));
                    row.put("maxZ", rs.getObject("maxZ"));
                    row.put("overlapping_area_id", rs.getObject("overlapping_area_id"));
                    row.put("overlapping_claim_uuid", rs.getString("overlapping_claim_uuid"));
                    row.put("overlapping_claim_name", rs.getString("overlapping_claim_name"));
                    return row;
                }
        ).stream()
                .filter(this::isDisallowedOverlap)
                .toList();
    }

    private boolean isDisallowedOverlap(Map<String, Object> row) {
        String claimUuid = (String) row.get("claim_uuid");
        String overlappingClaimUuid = (String) row.get("overlapping_claim_uuid");
        if (claimUuid.equals(overlappingClaimUuid)) {
            row.put("integrity_error", "claim has overlapping areas with itself");
            return true;
        }

        Set<String> claimAncestors = new HashSet<>(ClaimManager.fetchAncestorClaimUuids(claimUuid));
        if (claimAncestors.contains(overlappingClaimUuid)) {
            return false;
        }
        Set<String> overlappingClaimAncestors = new HashSet<>(ClaimManager.fetchAncestorClaimUuids(overlappingClaimUuid));
        if (overlappingClaimAncestors.contains(claimUuid)) {
            return false;
        }

        row.put("integrity_error", "overlap is not between an ancestor and descendant claim");
        return true;
    }
}
