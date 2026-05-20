package cn.jason31416.betterresidence.handler.checkers;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.DataIntegrityHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MissingClaimReferencesChecker implements DataIntegrityHandler.IntegrityChecker {
    @Override
    public String name() {
        return "missing-claim-references";
    }

    @Override
    public String description() {
        return "All claim_uuid and parent_uuid references must point to existing claim rows.";
    }

    @Override
    public List<Map<String, Object>> findCorruptedRows() {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT 'claim.parent_uuid' AS source,
                       child.uuid AS claim_uuid,
                       child.parent_uuid AS missing_claim_uuid,
                       NULL AS row_key
                FROM claim child
                LEFT JOIN claim parent ON parent.uuid = child.parent_uuid
                WHERE child.parent_uuid IS NOT NULL
                  AND parent.uuid IS NULL

                UNION ALL

                SELECT 'claim_areas.claim_uuid' AS source,
                       claim_areas.claim_uuid AS claim_uuid,
                       claim_areas.claim_uuid AS missing_claim_uuid,
                       CAST(claim_areas.area_id AS TEXT) AS row_key
                FROM claim_areas
                LEFT JOIN claim ON claim.uuid = claim_areas.claim_uuid
                WHERE claim.uuid IS NULL

                UNION ALL

                SELECT 'claim_permissions.claim_uuid' AS source,
                       claim_permissions.claim_uuid AS claim_uuid,
                       claim_permissions.claim_uuid AS missing_claim_uuid,
                       claim_permissions.permission AS row_key
                FROM claim_permissions
                LEFT JOIN claim ON claim.uuid = claim_permissions.claim_uuid
                WHERE claim.uuid IS NULL

                UNION ALL

                SELECT 'group_weights.claim_uuid' AS source,
                       group_weights.claim_uuid AS claim_uuid,
                       group_weights.claim_uuid AS missing_claim_uuid,
                       group_weights.group_id AS row_key
                FROM group_weights
                LEFT JOIN claim ON claim.uuid = group_weights.claim_uuid
                WHERE claim.uuid IS NULL

                UNION ALL

                SELECT 'player_groups.claim_uuid' AS source,
                       player_groups.claim_uuid AS claim_uuid,
                       player_groups.claim_uuid AS missing_claim_uuid,
                       player_groups.player_uuid AS row_key
                FROM player_groups
                LEFT JOIN claim ON claim.uuid = player_groups.claim_uuid
                WHERE claim.uuid IS NULL

                UNION ALL

                SELECT 'claim_flags.claim_uuid' AS source,
                       claim_flags.claim_uuid AS claim_uuid,
                       claim_flags.claim_uuid AS missing_claim_uuid,
                       claim_flags.flag AS row_key
                FROM claim_flags
                LEFT JOIN claim ON claim.uuid = claim_flags.claim_uuid
                WHERE claim.uuid IS NULL

                ORDER BY source ASC, claim_uuid ASC, row_key ASC
                """,
                List.of(),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("source", rs.getString("source"));
                    row.put("claim_uuid", rs.getString("claim_uuid"));
                    row.put("missing_claim_uuid", rs.getString("missing_claim_uuid"));
                    row.put("row_key", rs.getString("row_key"));
                    return row;
                }
        );
    }
}
