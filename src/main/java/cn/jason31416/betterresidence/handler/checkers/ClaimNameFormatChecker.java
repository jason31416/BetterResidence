package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.core.ClaimNameValidator;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;
import cn.jason31416.planetlib.util.MapTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClaimNameFormatChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "claim-name-format";
    }

    @Override
    public String description() {
        return "Claim names must fully match the configured claim.name-regex.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        List<Map<String, Object>> corruptedRows = new ArrayList<>();
        for (MapTree row : DataHandler.getDatabase().select("claim").list()) {
            String uuid = row.getString("uuid", null);
            String name = row.getString("name", null);
            if (ClaimNameValidator.isValid(name)) {
                continue;
            }
            Map<String, Object> corruptedRow = new LinkedHashMap<>();
            corruptedRow.put("uuid", uuid);
            corruptedRow.put("name", name);
            corruptedRow.put("regex", ClaimNameValidator.getRegex());
            corruptedRow.put("integrity_error", "claim name does not match configured regex");
            corruptedRows.add(corruptedRow);
        }
        return corruptedRows;
    }
}
