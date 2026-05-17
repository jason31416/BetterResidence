package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.planetlib.data.Database;
import cn.jason31416.planetlib.data.SQLInstance;
import cn.jason31416.planetlib.data.TableSchema;
import cn.jason31416.planetlib.data.statement.CompiledSql;
import cn.jason31416.planetlib.data.type.IntegerColumn;
import cn.jason31416.planetlib.data.type.StringColumn;
import lombok.Getter;

import java.io.File;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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
                .addColumn("weight", new IntegerColumn()) // -1000..1000, minimum player group weight required for this permission.
                .addColumn("claim_uuid", new StringColumn())
        );
        database.registerTable(new TableSchema("group_weights") // Per-claim custom groups; configured groups are loaded from config.yml.
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
        initializeRegisteredTables();
        database.getSqlInstance().execute("CREATE INDEX IF NOT EXISTS idx_claim_areas_world_area ON claim_areas(world, area_id);", List.of());
        database.getSqlInstance().execute("CREATE INDEX IF NOT EXISTS idx_claim_areas_claim_uuid ON claim_areas(claim_uuid);", List.of());
        database.getSqlInstance().execute("CREATE INDEX IF NOT EXISTS idx_claim_parent_uuid ON claim(parent_uuid);", List.of());
    }

    public static void executeBatch(List<CompiledSql> statements) {
        if (statements.isEmpty()) {
            return;
        }

        SQLInstance sqlInstance = getDatabase().getSqlInstance();
        try (var connection = sqlInstance.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                for (CompiledSql statement : statements) {
                    try (var preparedStatement = connection.prepareStatement(statement.sql())) {
                        SQLInstance.bindParams(preparedStatement, statement.params());
                        preparedStatement.executeUpdate();
                    }
                }

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to execute SQL batch", exception);
        }
    }

    public static void close() {
        getDatabase().getSqlInstance().close();
    }

    private static void initializeRegisteredTables() {
        SQLInstance sqlInstance = getDatabase().getSqlInstance();
        try (var connection = sqlInstance.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (TableSchema tableSchema : getDatabase().getTables().values()) {
                if (!tableExists(metaData, tableSchema.getTableName())) {
                    executeStatement(connection, sqlInstance.getDialect().createTableSql(tableSchema));
                    continue;
                }

                Set<String> existingColumns = getExistingColumns(metaData, tableSchema.getTableName());
                for (var entry : tableSchema.getColumns().entrySet()) {
                    if (existingColumns.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    executeStatement(connection, sqlInstance.getDialect().addColumnSql(
                            tableSchema.getTableName(),
                            entry.getKey(),
                            entry.getValue()
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize database schema", exception);
        }
    }

    private static void executeStatement(java.sql.Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private static boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet tables = metaData.getTables(null, null, tableName, null)) {
            while (tables.next()) {
                String existingTableName = tables.getString("TABLE_NAME");
                if (existingTableName != null && existingTableName.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> getExistingColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        Set<String> existingColumns = new TreeSet<>();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                if (columnName != null) {
                    existingColumns.add(columnName.toLowerCase(Locale.ROOT));
                }
            }
        }
        return existingColumns;
    }
}
