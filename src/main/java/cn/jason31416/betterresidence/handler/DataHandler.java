package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.planetlib.data.Database;
import cn.jason31416.planetlib.data.TableSchema;
import cn.jason31416.planetlib.data.type.BooleanColumn;
import cn.jason31416.planetlib.data.type.IntegerColumn;
import cn.jason31416.planetlib.data.type.StringColumn;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataHandler {
    @Getter
    private static Database database;

    public static void init() {
        File dataFolder = BetterResidence.getInstance().getDataFolder();
        File dbFile = new File(dataFolder, "database.db");

        database = Database.createSqlite(dbFile);

        database.getSqlInstance().execute("CREATE VIRTUAL TABLE IF NOT EXISTS area USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ);", List.of());
        database.registerTable(new TableSchema("claim")
                .addColumn("uuid", new StringColumn().setPrimaryKey(true).setUnique(true))
                .addColumn("name", new StringColumn())
                .addColumn("owner_uuid", new StringColumn())
        );
        database.registerTable(new TableSchema("claim_areas")
                .addColumn("area_id", new IntegerColumn().setPrimaryKey(true).setUnique(true))
                .addColumn("claim_uuid", new StringColumn())
        );
        database.registerTable(new TableSchema("claim_permissions")
                .addColumn("permission", new StringColumn()) // bukkit permission like permission nodes, e.g. block.break:oak or entity.kill:animals or block.interact (which is equiv to block.interact:all) or misc.teleport, etc.
                .addColumn("weight", new IntegerColumn()) // 0-1000, 1000 means applicable to everyone except owner, default is 900 (applicable to everyone except owner and trusted)
                .addColumn("value", new BooleanColumn()) // allow or disallow
                .addColumn("claim_uuid", new StringColumn())
        );
        // Non-player environmental settings such as weather, time, etc., all values can be string, integer, or bool, or even double or whatever they like
        database.registerTable(new TableSchema("claim_flags")
                .addColumn("flag", new StringColumn())
                .addColumn("value", new StringColumn())
                .addColumn("claim_uuid", new StringColumn())
        );
    }

    public static void close() {
        getDatabase().getSqlInstance().close();
    }
}
