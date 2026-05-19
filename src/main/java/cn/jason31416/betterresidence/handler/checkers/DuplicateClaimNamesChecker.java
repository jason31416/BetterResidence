package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DuplicateClaimNamesChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "duplicate-claim-names";
    }

    @Override
    public String description() {
        return "Claim names are expected to be unique because commands resolve claims by name before UUID.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT claim.uuid,
                       claim.name,
                       claim.owner_uuid,
                       claim.parent_uuid
                FROM claim
                JOIN (
                    SELECT name
                    FROM claim
                    GROUP BY name
                    HAVING COUNT(*) > 1
                ) duplicates ON duplicates.name = claim.name
                ORDER BY claim.name ASC, claim.uuid ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("owner_uuid", rs.getString("owner_uuid"));
                    row.put("parent_uuid", rs.getString("parent_uuid"));
                    return row;
                }
        );
    }
}
