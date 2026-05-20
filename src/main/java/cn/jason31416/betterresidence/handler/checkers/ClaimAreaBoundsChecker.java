package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClaimAreaBoundsChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "claim-area-bounds";
    }

    @Override
    public String description() {
        return "Area bounds must be complete and ordered so AreaBox can be constructed safely.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT id AS area_id, minX, maxX, minY, maxY, minZ, maxZ
                FROM area
                WHERE minX IS NULL OR maxX IS NULL OR minY IS NULL OR maxY IS NULL OR minZ IS NULL OR maxZ IS NULL
                   OR minX > maxX OR minY > maxY OR minZ > maxZ
                ORDER BY id ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("area_id", rs.getObject("area_id"));
                    row.put("minX", rs.getObject("minX"));
                    row.put("maxX", rs.getObject("maxX"));
                    row.put("minY", rs.getObject("minY"));
                    row.put("maxY", rs.getObject("maxY"));
                    row.put("minZ", rs.getObject("minZ"));
                    row.put("maxZ", rs.getObject("maxZ"));
                    return row;
                }
        );
    }
}
