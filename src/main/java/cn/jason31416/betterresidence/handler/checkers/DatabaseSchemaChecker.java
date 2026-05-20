package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DatabaseSchemaChecker implements DataIntegrityHandler.IntegrityChecker {
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "area",
            "claim",
            "claim_areas",
            "claim_permissions",
            "group_weights",
            "player_groups",
            "claim_flags"
    );

    @Override
    public String name() {
        return "database-schema";
    }

    @Override
    public String description() {
        return "The SQLite database must contain the expected tables and pass SQLite integrity_check.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        List<Map<String, Object>> tableRows = DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT name, type
                FROM sqlite_master
                WHERE name IN ('area', 'claim', 'claim_areas', 'claim_permissions', 'group_weights', 'player_groups', 'claim_flags')
                ORDER BY name ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("type", rs.getString("type"));
                    return row;
                }
        );
        Set<String> existingTables = tableRows.stream()
                .map(row -> (String) row.get("name"))
                .collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> corruptedRows = new java.util.ArrayList<>();
        for (String expectedTable : EXPECTED_TABLES) {
            if (!existingTables.contains(expectedTable)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("integrity_error", "missing expected table");
                row.put("table", expectedTable);
                corruptedRows.add(row);
            }
        }

        corruptedRows.addAll(DataHandler.getDatabase().getSqlInstance().executeQuery(
                "PRAGMA integrity_check;",
                List.of(),
                rs -> {
                    String result = rs.getString(1);
                    if ("ok".equalsIgnoreCase(result)) {
                        return null;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("integrity_error", "sqlite integrity_check failed");
                    row.put("result", result);
                    return row;
                }
        ).stream().filter(row -> row != null).toList());
        return corruptedRows;
    }
}
