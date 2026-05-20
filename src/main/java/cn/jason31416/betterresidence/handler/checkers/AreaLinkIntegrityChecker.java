package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AreaLinkIntegrityChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "area-link-integrity";
    }

    @Override
    public String description() {
        return "Every area row must have exactly one claim_areas link, and every claim_areas link must point to an area.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT 'missing-area' AS integrity_error,
                       claim_areas.area_id AS area_id,
                       claim_areas.claim_uuid AS claim_uuid,
                       claim_areas.world AS world
                FROM claim_areas
                LEFT JOIN area ON area.id = claim_areas.area_id
                WHERE area.id IS NULL

                UNION ALL

                SELECT 'orphan-area' AS integrity_error,
                       area.id AS area_id,
                       NULL AS claim_uuid,
                       NULL AS world
                FROM area
                LEFT JOIN claim_areas ON claim_areas.area_id = area.id
                WHERE claim_areas.area_id IS NULL

                ORDER BY integrity_error ASC, area_id ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("integrity_error", rs.getString("integrity_error"));
                    row.put("area_id", rs.getObject("area_id"));
                    row.put("claim_uuid", rs.getString("claim_uuid"));
                    row.put("world", rs.getString("world"));
                    return row;
                }
        );
    }
}
