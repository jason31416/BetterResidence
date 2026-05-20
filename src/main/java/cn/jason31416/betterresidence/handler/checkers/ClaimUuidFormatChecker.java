package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;
import cn.jason31416.planetlib.util.MapTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimUuidFormatChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "uuid-format";
    }

    @Override
    public String description() {
        return "Stored UUID fields must contain valid UUID strings.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        List<Map<String, Object>> corruptedRows = new ArrayList<>();
        for (MapTree row : DataHandler.getDatabase().select("claim").list()) {
            checkUuid(corruptedRows, "claim.uuid", row.getString("uuid", null), row.getString("uuid", null));
            checkUuid(corruptedRows, "claim.owner_uuid", row.getString("owner_uuid", null), row.getString("uuid", null));
            checkNullableUuid(corruptedRows, "claim.parent_uuid", row.getString("parent_uuid", null), row.getString("uuid", null));
        }
        for (MapTree row : DataHandler.getDatabase().select("player_groups").list()) {
            checkUuid(corruptedRows, "player_groups.player_uuid", row.getString("player_uuid", null), row.getString("claim_uuid", null));
        }
        for (MapTree row : DataHandler.getDatabase().select("claim_areas").list()) {
            checkUuid(corruptedRows, "claim_areas.world", row.getString("world", null), row.getString("claim_uuid", null));
        }
        return corruptedRows;
    }

    private void checkNullableUuid(List<Map<String, Object>> corruptedRows, String source, String value, String ownerKey) {
        if (value == null) {
            return;
        }
        checkUuid(corruptedRows, source, value, ownerKey);
    }

    private void checkUuid(List<Map<String, Object>> corruptedRows, String source, String value, String ownerKey) {
        if (value == null || value.isBlank()) {
            addCorruptedRow(corruptedRows, source, value, ownerKey, "missing uuid");
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            addCorruptedRow(corruptedRows, source, value, ownerKey, "invalid uuid format");
        }
    }

    private void addCorruptedRow(List<Map<String, Object>> corruptedRows, String source, String value, String ownerKey, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("value", value);
        row.put("owner_key", ownerKey);
        row.put("integrity_error", error);
        corruptedRows.add(row);
    }
}
