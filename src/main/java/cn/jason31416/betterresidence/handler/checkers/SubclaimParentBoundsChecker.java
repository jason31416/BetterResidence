package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.claim.AreaBox;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SubclaimParentBoundsChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "subclaim-parent-bounds";
    }

    @Override
    public String description() {
        return "Every subclaim area must be completely covered by the union of its parent claim areas in the same world.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT child.uuid AS child_uuid,
                       child.name AS child_name,
                       child.parent_uuid AS parent_uuid,
                       parent.name AS parent_name,
                       claim_areas.area_id AS area_id,
                       claim_areas.world AS world,
                       area.minX,
                       area.maxX,
                       area.minY,
                       area.maxY,
                       area.minZ,
                       area.maxZ
                FROM claim child
                LEFT JOIN claim parent ON parent.uuid = child.parent_uuid
                LEFT JOIN claim_areas ON claim_areas.claim_uuid = child.uuid
                LEFT JOIN area ON area.id = claim_areas.area_id
                WHERE child.parent_uuid IS NOT NULL
                ORDER BY child.name ASC, child.uuid ASC, claim_areas.area_id ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("child_uuid", rs.getString("child_uuid"));
                    row.put("child_name", rs.getString("child_name"));
                    row.put("parent_uuid", rs.getString("parent_uuid"));
                    row.put("parent_name", rs.getString("parent_name"));
                    row.put("area_id", rs.getObject("area_id"));
                    row.put("world", rs.getString("world"));
                    row.put("minX", rs.getObject("minX"));
                    row.put("maxX", rs.getObject("maxX"));
                    row.put("minY", rs.getObject("minY"));
                    row.put("maxY", rs.getObject("maxY"));
                    row.put("minZ", rs.getObject("minZ"));
                    row.put("maxZ", rs.getObject("maxZ"));
                    return row;
                }
        ).stream()
                .filter(this::isCorrupted)
                .toList();
    }

    private boolean isCorrupted(Map<String, Object> row) {
        if (row.get("parent_name") == null) {
            row.put("integrity_error", "parent claim is missing");
            return true;
        }
        if (row.get("area_id") == null || row.get("world") == null) {
            row.put("integrity_error", "subclaim has no linked area");
            return true;
        }
        if (hasMissingAreaBound(row)) {
            row.put("integrity_error", "subclaim area link points to missing or incomplete area bounds");
            return true;
        }

        AreaBox areaBox = createAreaBox(row);
        String parentUuid = (String) row.get("parent_uuid");
        String worldUuid = (String) row.get("world");
        if (!ClaimManager.isAreaCoveredByClaim(parentUuid, worldUuid, areaBox)) {
            row.put("integrity_error", "subclaim area is not completely covered by parent claim areas");
            return true;
        }
        return false;
    }

    private boolean hasMissingAreaBound(Map<String, Object> row) {
        return row.get("minX") == null
                || row.get("maxX") == null
                || row.get("minY") == null
                || row.get("maxY") == null
                || row.get("minZ") == null
                || row.get("maxZ") == null;
    }

    private AreaBox createAreaBox(Map<String, Object> row) {
        return new AreaBox(
                toInt(row.get("minX")),
                toInt(row.get("maxX")),
                toInt(row.get("minY")),
                toInt(row.get("maxY")),
                toInt(row.get("minZ")),
                toInt(row.get("maxZ"))
        );
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }
}
