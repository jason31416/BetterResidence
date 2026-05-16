package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.planetlib.data.Database;
import cn.jason31416.planetlib.data.TableSchema;
import cn.jason31416.planetlib.data.type.IntegerColumn;
import cn.jason31416.planetlib.data.type.StringColumn;
import lombok.Getter;

import java.io.File;
import java.util.List;

public class DataHandler {
    @Getter
    private static Database database;

    public static void init() {
        File dataFolder = BetterResidence.getInstance().getDataFolder();
        File dbFile = new File(dataFolder, "database.db");

        database = Database.createSqlite(dbFile);

        database.getSqlInstance().execute("CREATE VIRTUAL TABLE IF NOT EXISTS area USING rtree_i32(id, minX, maxX, minY, maxY, minZ, maxZ);", List.of());
        database.registerTable(new TableSchema("claim")
                .addColumn("uuid", new StringColumn().setPrimaryKey(true).setUnique(true))
                .addColumn("name", new StringColumn())
                .addColumn("owner_uuid", new StringColumn())
                .addColumn("parent_uuid", new StringColumn())
        );
        database.registerTable(new TableSchema("claim_areas")
                .addColumn("area_id", new IntegerColumn().setPrimaryKey(true).setUnique(true))
                .addColumn("claim_uuid", new StringColumn())
                .addColumn("world", new StringColumn())
        );
        database.registerTable(new TableSchema("claim_permissions")
                .addColumn("permission", new StringColumn()) // bukkit permission like permission node keys, e.g. block.break:oak or entity.kill:animals or block.interact (which is equiv to block.interact:all) or misc.teleport, etc.
                .addColumn("weight", new IntegerColumn()) // 0-1000, minimum player group weight required for this permission.
                .addColumn("claim_uuid", new StringColumn())
        );
        database.registerTable(new TableSchema("group_weights") // Each residence can have multiple groups. This table will store all groups BESIDES the three default (0 everyone, 900 trusted, 1000 owner)
                .addColumn("group_id", new StringColumn())
                .addColumn("weight", new IntegerColumn())
                .addColumn("group_name", new StringColumn())
                .addColumn("claim_uuid", new StringColumn())
        );
        database.registerTable(new TableSchema("player_groups") // Each player can belong in a single group in the claim.
                .addColumn("player_uuid", new StringColumn())
                .addColumn("group_id", new StringColumn())
                .addColumn("claim_uuid", new StringColumn())
        );
        // Non-player environmental settings such as weather, time, etc., all values can be string, integer, or bool, or even double or whatever they like
        database.registerTable(new TableSchema("claim_flags")
                .addColumn("flag", new StringColumn())
                .addColumn("value", new StringColumn())
                .addColumn("claim_uuid", new StringColumn())
        );
        database.initializeSchema();
        database.getSqlInstance().execute("CREATE INDEX IF NOT EXISTS idx_claim_areas_world_area ON claim_areas(world, area_id);", List.of());
        database.getSqlInstance().execute("CREATE INDEX IF NOT EXISTS idx_claim_areas_claim_uuid ON claim_areas(claim_uuid);", List.of());
        database.getSqlInstance().execute("CREATE INDEX IF NOT EXISTS idx_claim_parent_uuid ON claim(parent_uuid);", List.of());
    }

    public static void close() {
        getDatabase().getSqlInstance().close();
    }
}
