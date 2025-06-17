// File: LimboAuth/src/main/java/net/elytrium/limboauth/service/DatabaseService.java
package net.elytrium.limboauth.service;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.db.DatabaseType;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableInfo;
import com.j256.ormlite.table.TableUtils;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.dependencies.DatabaseLibrary;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import org.slf4j.Logger;

/**
 * Service responsible for all database interactions. It manages the connection source, Data Access
 * Objects (DAOs), and provides methods for CRUD operations on {@link RegisteredPlayer} entities.
 * Also handles database schema migration.
 */
public class DatabaseService {

  private final Logger logger;
  private ConnectionSource connectionSource;
  private Dao<RegisteredPlayer, String> playerDao;
  private Path dataDirectoryPath;
  private ConfigManager configManager;
  private boolean initialized = false;

  /**
   * Constructs the DatabaseService.
   *
   * @param logger The logger for this service.
   */
  /** Default constructor. */
  public DatabaseService(Logger logger) {
    this.logger = logger;
  }

  /**
   * Initializes the database connection, creates tables if they don't exist, and performs schema
   * migration.
   *
   * @param dataDirectoryPath The plugin's data directory path.
   * @param configManager The configuration manager for database settings.
   * @throws RuntimeException if database initialization fails.
   */
  public void initialize(Path dataDirectoryPath, ConfigManager configManager) {
    this.dataDirectoryPath = dataDirectoryPath;
    this.configManager = configManager;
    this.logger.info("Initializing DatabaseService...");

    Settings.DATABASE dbConfig = this.configManager.getSettings().DATABASE;
    DatabaseLibrary databaseLibrary = dbConfig.STORAGE_TYPE;

    try {
      this.connectionSource =
          databaseLibrary.connectToORM(
              this.dataDirectoryPath.toAbsolutePath(),
              dbConfig.HOSTNAME,
              dbConfig.DATABASE + dbConfig.CONNECTION_PARAMETERS,
              dbConfig.USER,
              dbConfig.PASSWORD);

      this.playerDao = DaoManager.createDao(this.connectionSource, RegisteredPlayer.class);

      if (TableUtils.createTableIfNotExists(this.connectionSource, RegisteredPlayer.class) == 1) {
        this.logger.info("Created RegisteredPlayer table in database.");
      } else {
        this.logger.debug("RegisteredPlayer table already exists.");
      }

      migrateDb();

      initialized = true;
      this.logger.info(
          "DatabaseService initialized successfully with type: {}. Registered players: {}",
          dbConfig.STORAGE_TYPE,
          getRegisteredPlayerCount());

    } catch (SQLException | IOException | URISyntaxException | ReflectiveOperationException e) {
      this.logger.error("Failed to initialize database connection", e);
      initialized = false;
      throw new RuntimeException("Database initialization failed", e);
    }
  }

  /**
   * Reloads the database service. If core database connection parameters have changed, it
   * re-initializes the connection. Otherwise, it just re-applies migrations.
   *
   * @param newConfigManager The new configuration manager.
   */
  public void reload(ConfigManager newConfigManager) {
    this.logger.info("Reloading DatabaseService...");
    Settings.DATABASE oldDbConfig = this.configManager.getSettings().DATABASE;
    Settings.DATABASE newDbConfig = newConfigManager.getSettings().DATABASE;

    boolean reinitializeRequired =
        !oldDbConfig.STORAGE_TYPE.equals(newDbConfig.STORAGE_TYPE)
            || !oldDbConfig.HOSTNAME.equals(newDbConfig.HOSTNAME)
            || !oldDbConfig.DATABASE.equals(newDbConfig.DATABASE)
            || !oldDbConfig.USER.equals(newDbConfig.USER)
            || !oldDbConfig.PASSWORD.equals(newDbConfig.PASSWORD);

    if (reinitializeRequired) {
      this.logger.warn(
          "Database configuration has changed significantly. Re-initializing the entire DatabaseService.");
      closeDataSource();
      initialize(this.dataDirectoryPath, newConfigManager);
    } else {
      this.configManager = newConfigManager;
      migrateDb();
      this.logger.info("DatabaseService configuration reloaded. Schema migration check performed.");
    }
  }

  private void migrateDb() {
    if (!isInitialized()) {
      this.logger.error("Cannot migrate database, DatabaseService is not initialized.");
      return;
    }
    this.logger.info("Starting database migration check...");
    TableInfo<RegisteredPlayer, String> tableInfo;
    try {
      // FIX APPLIED HERE: Use supported TableInfo constructor
      DatabaseType databaseType = this.connectionSource.getDatabaseType();
      tableInfo = new TableInfo<RegisteredPlayer, String>(databaseType, RegisteredPlayer.class);
    } catch (SQLException e) {
      this.logger.error("Failed to get TableInfo for RegisteredPlayer during migration.", e);
      throw new SQLRuntimeException("Failed to get TableInfo for migration.", e);
    }

    Set<String> currentFieldsInClass = new HashSet<>();
    for (FieldType fieldType : tableInfo.getFieldTypes()) {
      currentFieldsInClass.add(fieldType.getColumnName().toLowerCase(Locale.ROOT));
    }

    String tableName = tableInfo.getTableName();
    Settings settings = this.configManager.getSettings();
    String databaseName = settings.DATABASE.DATABASE;
    DatabaseLibrary databaseLibrary = settings.DATABASE.STORAGE_TYPE;
    String findSql;

    switch (databaseLibrary) {
      case SQLITE:
        findSql = "SELECT name FROM PRAGMA_TABLE_INFO('" + tableName + "')";
        break;
      case H2:
      case H2_LEGACY_V1:
        findSql =
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = '"
                + tableName.toUpperCase()
                + "'";
        break;
      case POSTGRESQL:
        findSql =
            "SELECT column_name FROM information_schema.columns WHERE table_catalog = ? AND table_schema = current_schema() AND table_name = ?";
        break;
      case MARIADB:
      case MYSQL:
        findSql =
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        break;
      default:
        this.logger.error("Unsupported database type for migration: {}", databaseLibrary);
        return;
    }
    this.logger.debug(
        "Executing migration column check for table '{}' with SQL (parameters will be bound if applicable): {}",
        tableName,
        findSql);

    try {
      try (GenericRawResults<String[]> queryResult =
          (databaseLibrary == DatabaseLibrary.POSTGRESQL
                  || databaseLibrary == DatabaseLibrary.MYSQL
                  || databaseLibrary == DatabaseLibrary.MARIADB)
              ? playerDao.queryRaw(findSql, databaseName, tableName)
              : playerDao.queryRaw(findSql)) {

        Set<String> columnsInDb = new HashSet<>();
        queryResult.forEach(result -> columnsInDb.add(result[0].toLowerCase(Locale.ROOT)));

        Set<FieldType> fieldsToAdd = new HashSet<>();
        for (FieldType fieldType : tableInfo.getFieldTypes()) {
          if (!columnsInDb.contains(fieldType.getColumnName().toLowerCase(Locale.ROOT))) {
            fieldsToAdd.add(fieldType);
          }
        }

        if (fieldsToAdd.isEmpty()) {
          this.logger.info(
              "No schema changes detected for table '{}'. Migration not needed.", tableName);
        } else {
          this.logger.info(
              "Schema changes detected for table '{}'. Applying migrations for columns: {}",
              tableName,
              fieldsToAdd.stream().map(FieldType::getColumnName).collect(Collectors.joining(", ")));

          DatabaseType dbType = this.playerDao.getConnectionSource().getDatabaseType();
          for (FieldType field : fieldsToAdd) {
            try {
              StringBuilder builder = new StringBuilder("ALTER TABLE ");
              dbType.appendEscapedEntityName(builder, tableName);
              builder.append(" ADD COLUMN ");
              dbType.appendColumnArg(
                  tableName,
                  builder,
                  field,
                  Collections.emptyList(),
                  Collections.emptyList(),
                  Collections.emptyList(),
                  Collections.emptyList());

              this.logger.info("Executing DDL for migration: {}", builder.toString());
              playerDao.executeRawNoArgs(builder.toString());
              this.logger.info(
                  "Successfully added column '{}' to table '{}'.",
                  field.getColumnName(),
                  tableName);
            } catch (SQLException e) {
              String sqlState = e.getSQLState();
              String errorMsgLower =
                  e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
              if ((sqlState != null && (sqlState.equals("42S21") || sqlState.equals("42701")))
                  || errorMsgLower.contains("duplicate column name")
                  || errorMsgLower.contains("column already exists")
                  || (databaseLibrary == DatabaseLibrary.SQLITE
                      && errorMsgLower.contains("duplicate column name"))) {
                this.logger.warn(
                    "Column '{}' in table '{}' likely already exists (or another schema issue occurred). Skipping addition. Error: {}",
                    field.getColumnName(),
                    tableName,
                    e.getMessage());
              } else {
                this.logger.error(
                    "Failed to migrate column '{}' for table '{}': {}",
                    field.getColumnName(),
                    tableName,
                    e.getMessage(),
                    e);
              }
            }
          }
        }
      }
    } catch (SQLException e) {
      this.logger.error(
          "Failed to query existing columns or execute migration for table '{}': {}",
          tableName,
          e.getMessage(),
          e);
      throw new SQLRuntimeException(
          "Error during database migration query or execution for table " + tableName, e);
    } catch (Exception e) {
      this.logger.error(
          "Unexpected error during database migration resource handling for table '{}': {}",
          tableName,
          e.getMessage(),
          e);
      throw new SQLRuntimeException(
          "Unexpected error during database migration resource handling for table " + tableName, e);
    }
    this.logger.info("Database migration check completed for table '{}'.", tableName);
  }

  /**
   * Finds a player by their lowercase nickname.
   *
   * @param lowercaseNickname The lowercase nickname.
   * @return The {@link RegisteredPlayer} or null if not found.
   * @throws SQLRuntimeException if a database query error occurs.
   * @throws IllegalStateException if the service is not initialized.
   */
  public RegisteredPlayer findPlayerByLowercaseNickname(String lowercaseNickname) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    try {
      return playerDao.queryForId(lowercaseNickname.toLowerCase(Locale.ROOT));
    } catch (SQLException e) {
      this.logger.error("Error finding player by lowercase nickname: {}", lowercaseNickname, e);
      throw new SQLRuntimeException("Database query failed", e);
    }
  }

  /**
   * Finds a player by their nickname (case-insensitive).
   *
   * @param nickname The nickname.
   * @return The {@link RegisteredPlayer} or null if not found.
   */
  public RegisteredPlayer findPlayerByNickname(String nickname) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    return findPlayerByLowercaseNickname(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Finds a player by their general UUID (can be online or offline mode UUID).
   *
   * @param uuid The UUID.
   * @return The {@link RegisteredPlayer} or null if not found.
   * @throws SQLRuntimeException if a database query error occurs.
   * @throws IllegalStateException if the service is not initialized.
   */
  public RegisteredPlayer findPlayerByUUID(UUID uuid) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    try {
      QueryBuilder<RegisteredPlayer, String> qb = playerDao.queryBuilder();
      qb.where().eq(RegisteredPlayer.UUID_FIELD, uuid.toString());
      return qb.queryForFirst();
    } catch (SQLException e) {
      this.logger.error("Error finding player by UUID '{}': {}", uuid, e.getMessage(), e);
      throw new SQLRuntimeException(e);
    }
  }

  /**
   * Finds a player by their premium (online-mode) UUID.
   *
   * @param premiumUuid The premium UUID.
   * @return The {@link RegisteredPlayer} or null if not found.
   * @throws SQLRuntimeException if a database query error occurs.
   * @throws IllegalStateException if the service is not initialized.
   */
  public RegisteredPlayer findPlayerByPremiumUUID(UUID premiumUuid) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    try {
      QueryBuilder<RegisteredPlayer, String> qb = playerDao.queryBuilder();
      qb.where().eq(RegisteredPlayer.PREMIUM_UUID_FIELD, premiumUuid.toString());
      return qb.queryForFirst();
    } catch (SQLException e) {
      this.logger.error(
          "Error finding player by premium UUID '{}': {}", premiumUuid, e.getMessage(), e);
      throw new SQLRuntimeException(e);
    }
  }

  /**
   * Creates a new player record in the database.
   *
   * @param player The {@link RegisteredPlayer} to create.
   * @throws SQLRuntimeException if the database operation fails.
   * @throws IllegalStateException if the service is not initialized.
   */
  public void createPlayer(RegisteredPlayer player) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    try {
      playerDao.create(player);
      this.logger.info("Created player record for: {}", player.getLowercaseNickname());
    } catch (SQLException e) {
      this.logger.error("Error creating player: {}", player.getLowercaseNickname(), e);
      throw new SQLRuntimeException("Failed to create player", e);
    }
  }

  /**
   * Updates an existing player record in the database.
   *
   * @param player The {@link RegisteredPlayer} to update.
   * @throws SQLRuntimeException if the database operation fails.
   * @throws IllegalStateException if the service is not initialized.
   */
  public void updatePlayer(RegisteredPlayer player) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    try {
      playerDao.update(player);
      this.logger.debug("Updated player record for: {}", player.getLowercaseNickname());
    } catch (SQLException e) {
      this.logger.error("Error updating player: {}", player.getLowercaseNickname(), e);
      throw new SQLRuntimeException("Failed to update player", e);
    }
  }

  /**
   * Deletes a player record from the database by their lowercase nickname.
   *
   * @param lowercaseNickname The lowercase nickname of the player to delete.
   * @throws SQLRuntimeException if the database operation fails.
   * @throws IllegalStateException if the service is not initialized.
   */
  public void deletePlayerByLowercaseNickname(String lowercaseNickname) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    try {
      int deleted = playerDao.deleteById(lowercaseNickname.toLowerCase(Locale.ROOT));
      if (deleted > 0) {
        this.logger.info("Deleted player record for: {}", lowercaseNickname);
      } else {
        this.logger.warn(
            "No player found to delete with lowercase nickname: {}", lowercaseNickname);
      }
    } catch (SQLException e) {
      this.logger.error("Error deleting player: {}", lowercaseNickname, e);
      throw new SQLRuntimeException("Failed to delete player", e);
    }
  }

  /**
   * Deletes a player record from the database by their nickname (case-insensitive).
   *
   * @param nickname The nickname of the player to delete.
   */
  public void deletePlayer(String nickname) {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    deletePlayerByLowercaseNickname(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Gets the total count of registered players in the database.
   *
   * @return The number of registered players, or 0 if an error occurs or not initialized.
   */
  public long getRegisteredPlayerCount() {
    if (!isInitialized() || playerDao == null) return 0;
    try {
      return playerDao.countOf();
    } catch (SQLException e) {
      this.logger.error("Error counting players", e);
      return 0;
    }
  }

  /**
   * Checks if the database service has been successfully initialized.
   *
   * @return True if initialized, false otherwise.
   */
  public boolean isInitialized() {
    return initialized;
  }

  /**
   * Gets the DAO for {@link RegisteredPlayer} entities.
   *
   * @return The player DAO.
   * @throws IllegalStateException if the service is not initialized.
   */
  public Dao<RegisteredPlayer, String> getPlayerDao() {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    return playerDao;
  }

  /**
   * Gets the underlying ORMLite {@link ConnectionSource}.
   *
   * @return The connection source.
   * @throws IllegalStateException if the service is not initialized.
   */
  public ConnectionSource getConnectionSource() {
    if (!isInitialized()) throw new IllegalStateException("DatabaseService not initialized.");
    return connectionSource;
  }

  /** Closes the database connection source and marks the service as uninitialized. */
  public void closeDataSource() {
    if (connectionSource != null) {
      try {
        DaoManager.clearCache();
        connectionSource.close();
        this.logger.info("Database connection closed successfully.");
      } catch (Exception e) {
        this.logger.error("Error closing database connection", e);
      }
    }
    initialized = false;
  }
}
