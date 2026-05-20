package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClaimHasAreaChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "claim-has-area";
    }

    @Override
    public String description() {
        return "Every claim must have at least one valid linked area.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT claim.uuid,
                       claim.name,
                       claim.owner_uuid,
                       claim.parent_uuid,
                       COUNT(area.id) AS valid_area_count
                FROM claim
                LEFT JOIN claim_areas ON claim_areas.claim_uuid = claim.uuid
                LEFT JOIN area ON area.id = claim_areas.area_id
                GROUP BY claim.uuid, claim.name, claim.owner_uuid, claim.parent_uuid
                HAVING COUNT(area.id) = 0
                ORDER BY claim.name ASC, claim.uuid ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("owner_uuid", rs.getString("owner_uuid"));
                    row.put("parent_uuid", rs.getString("parent_uuid"));
                    row.put("valid_area_count", rs.getInt("valid_area_count"));
                    return row;
                }
        );
    }
}
