package cn.jason31416.betterresidence.handler.checkers;

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

public final class ClaimTreeCycleChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "claim-tree-cycles";
    }

    @Override
    public String description() {
        return "Claim parent links must not form cycles.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        Map<String, String> parentByClaim = new HashMap<>();
        for (MapTree row : DataHandler.getDatabase().select("claim").list()) {
            parentByClaim.put(row.getString("uuid"), row.getString("parent_uuid", null));
        }

        List<Map<String, Object>> corruptedRows = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (String claimUuid : parentByClaim.keySet()) {
            List<String> path = new ArrayList<>();
            Set<String> seenInPath = new HashSet<>();
            String current = claimUuid;
            while (current != null) {
                if (!seenInPath.add(current)) {
                    if (reported.add(current)) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("claim_uuid", claimUuid);
                        row.put("cycle_at_uuid", current);
                        row.put("path", String.join(" -> ", path) + " -> " + current);
                        corruptedRows.add(row);
                    }
                    break;
                }
                path.add(current);
                current = parentByClaim.get(current);
            }
        }
        return corruptedRows;
    }
}
