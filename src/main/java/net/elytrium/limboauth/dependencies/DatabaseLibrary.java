package net.elytrium.limboauth.dependencies;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.jdbc.db.DatabaseTypeUtils;
import com.j256.ormlite.support.ConnectionSource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/**
 * Enum representing the supported database types and their connection logic. It manages JDBC driver
 * loading, connection string generation, and ORMLite connection source setup.
 */
public enum DatabaseLibrary {
  /** H2 Database legacy version 1. */
  H2_LEGACY_V1(
      BaseLibrary.H2_V1,
      (classLoader, dir, jdbc, user, password) ->
          fromDriver(classLoader.loadClass("org.h2.Driver"), jdbc, null, null, false),
      (dir, hostname, database) -> "jdbc:h2:" + dir + "/limboauth"),
  /** H2 Database current version. */
  H2(
      BaseLibrary.H2_V2,
      (classLoader, dir, jdbc, user, password) -> {
        Connection modernConnection =
            fromDriver(classLoader.loadClass("org.h2.Driver"), jdbc, null, null, true);

        Path legacyDatabase = dir.resolve("limboauth.mv.db");
        if (Files.exists(legacyDatabase)) {
          Path dumpFile = dir.resolve("limboauth.dump.sql");
          try (Connection legacyConnection =
              H2_LEGACY_V1.connect(dir, null, null, user, password)) {
            try (PreparedStatement migrateStatement =
                legacyConnection.prepareStatement(
                    "SCRIPT TO ?")) { // FIXED: Removed single quotes from placeholder
              migrateStatement.setString(1, dumpFile.toString());
              migrateStatement.execute();
            }
          }

          try (PreparedStatement migrateStatement =
              modernConnection.prepareStatement(
                  "RUNSCRIPT FROM ?")) { // FIXED: Removed single quotes from placeholder
            migrateStatement.setString(1, dumpFile.toString());
            migrateStatement.execute();
          }

          Files.delete(dumpFile);
          Files.move(legacyDatabase, dir.resolve("limboauth-v1-backup.mv.db"));
        }

        return modernConnection;
      },
      (dir, hostname, database) -> "jdbc:h2:" + dir + "/limboauth-v2"),
  /** MySQL database support. */
  MYSQL(
      BaseLibrary.MYSQL,
      (classLoader, dir, jdbc, user, password) ->
          fromDriver(
              classLoader.loadClass("com.mysql.cj.jdbc.NonRegisteringDriver"),
              jdbc,
              user,
              password,
              true),
      (dir, hostname, database) -> "jdbc:mysql://" + hostname + "/" + database),
  /** MariaDB database support. */
  MARIADB(
      BaseLibrary.MARIADB,
      (classLoader, dir, jdbc, user, password) ->
          fromDriver(classLoader.loadClass("org.mariadb.jdbc.Driver"), jdbc, user, password, true),
      (dir, hostname, database) -> "jdbc:mariadb://" + hostname + "/" + database),
  /** PostgreSQL database support. */
  POSTGRESQL(
      BaseLibrary.POSTGRESQL,
      (classLoader, dir, jdbc, user, password) ->
          fromDriver(classLoader.loadClass("org.postgresql.Driver"), jdbc, user, password, true),
      (dir, hostname, database) -> "jdbc:postgresql://" + hostname + "/" + database),
  /** SQLite database support. */
  SQLITE(
      BaseLibrary.SQLITE,
      (classLoader, dir, jdbc, user, password) ->
          fromDriver(classLoader.loadClass("org.sqlite.JDBC"), jdbc, user, password, true),
      (dir, hostname, database) -> "jdbc:sqlite:" + dir + "/limboauth.db");

  private final BaseLibrary baseLibrary;
  private final DatabaseConnector connector;
  private final DatabaseStringGetter stringGetter;
  private final IsolatedDriver driver =
      new IsolatedDriver("jdbc:limboauth_" + this.name().toLowerCase(Locale.ROOT) + ":");

  DatabaseLibrary(
      BaseLibrary baseLibrary, DatabaseConnector connector, DatabaseStringGetter stringGetter) {
    this.baseLibrary = baseLibrary;
    this.connector = connector;
    this.stringGetter = stringGetter;
  }

  /**
   * Establishes a database connection using a specific class loader.
   *
   * @param classLoader The class loader to use for loading the JDBC driver.
   * @param dir The data directory, used for file-based databases like H2 and SQLite.
   * @param hostname The hostname for network-based databases.
   * @param database The database name/schema.
   * @param user The database username.
   * @param password The database password.
   * @return A {@link Connection} to the database.
   * @throws ReflectiveOperationException If there's an issue loading or instantiating the driver.
   * @throws SQLException If a database access error occurs.
   * @throws IOException If an I/O error occurs (e.g., downloading driver or accessing files).
   */
  public Connection connect(
      ClassLoader classLoader,
      Path dir,
      String hostname,
      String database,
      String user,
      String password)
      throws ReflectiveOperationException, SQLException, IOException {
    return this.connect(
        classLoader, dir, this.stringGetter.getJdbcString(dir, hostname, database), user, password);
  }

  /**
   * Establishes a database connection using an isolated class loader for the driver.
   *
   * @param dir The data directory.
   * @param hostname The hostname.
   * @param database The database name.
   * @param user The username.
   * @param password The password.
   * @return A {@link Connection} to the database.
   * @throws ReflectiveOperationException If driver loading fails.
   * @throws SQLException If database access fails.
   * @throws IOException If an I/O error occurs.
   */
  public Connection connect(
      Path dir, String hostname, String database, String user, String password)
      throws ReflectiveOperationException, SQLException, IOException {
    return this.connect(
        dir, this.stringGetter.getJdbcString(dir, hostname, database), user, password);
  }

  /**
   * Establishes a database connection using a specific class loader and JDBC URL.
   *
   * @param classLoader The class loader.
   * @param dir The data directory.
   * @param jdbc The JDBC connection string.
   * @param user The username.
   * @param password The password.
   * @return A {@link Connection} to the database.
   * @throws ReflectiveOperationException If driver loading fails.
   * @throws SQLException If database access fails.
   * @throws IOException If an I/O error occurs.
   */
  /**
   * Establishes database connection.
   *
   * @param classLoader Class loader for JDBC driver
   * @param dir Data directory path
   * @param jdbc JDBC connection string
   * @param user Database username
   * @param password Database password
   * @return Database connection
   * @throws SQLException if connection fails
   */
  Connection connect(ClassLoader classLoader, Path dir, String jdbc, String user, String password)
      throws ReflectiveOperationException, SQLException, IOException {
    return this.connector.connect(classLoader, dir, jdbc, user, password);
  }

  /**
   * Establishes a database connection using an isolated class loader and JDBC URL.
   *
   * @param dir The data directory.
   * @param jdbc The JDBC connection string.
   * @param user The username.
   * @param password The password.
   * @return A {@link Connection} to the database.
   * @throws IOException If an I/O error occurs.
   * @throws ReflectiveOperationException If driver loading fails.
   * @throws SQLException If database access fails.
   */
  public Connection connect(Path dir, String jdbc, String user, String password)
      throws IOException, ReflectiveOperationException, SQLException {
    return this.connector.connect(
        new IsolatedClassLoader(new URL[] {this.baseLibrary.getClassLoaderURL()}),
        dir,
        jdbc,
        user,
        password);
  }

  /**
   * Creates an ORMLite {@link ConnectionSource} for this database type. This involves loading the
   * driver in an isolated class loader if not already done and registering an {@link
   * IsolatedDriver} wrapper.
   *
   * @param dir The data directory.
   * @param hostname The hostname.
   * @param database The database name.
   * @param user The username.
   * @param password The password.
   * @return A {@link ConnectionSource} for ORMLite.
   * @throws ReflectiveOperationException If driver loading/instantiation fails.
   * @throws IOException If an I/O error occurs.
   * @throws SQLException If database access fails.
   * @throws URISyntaxException If the JDBC URL is invalid.
   */
  public ConnectionSource connectToORM(
      Path dir, String hostname, String database, String user, String password)
      throws ReflectiveOperationException, IOException, SQLException, URISyntaxException {
    if (this.driver.getOriginal() == null) {
      IsolatedClassLoader classLoader =
          new IsolatedClassLoader(new URL[] {this.baseLibrary.getClassLoaderURL()});
      Class<?> driverClass =
          classLoader.loadClass(
              switch (this) {
                case H2_LEGACY_V1, H2 -> "org.h2.Driver";
                case MYSQL -> "com.mysql.cj.jdbc.NonRegisteringDriver";
                case MARIADB -> "org.mariadb.jdbc.Driver";
                case POSTGRESQL -> "org.postgresql.Driver";
                case SQLITE -> "org.sqlite.JDBC";
              });

      this.driver.setOriginal((Driver) driverClass.getConstructor().newInstance());
      DriverManager.registerDriver(this.driver);
    }

    String jdbc = this.stringGetter.getJdbcString(dir, hostname, database);
    boolean h2 = this.baseLibrary == BaseLibrary.H2_V1 || this.baseLibrary == BaseLibrary.H2_V2;
    return new JdbcPooledConnectionSource(
        this.driver.getInitializer() + jdbc,
        h2 ? null : user,
        h2 ? null : password,
        DatabaseTypeUtils.createDatabaseType(jdbc));
  }

  private static Connection fromDriver(
      Class<?> connectionClass, String jdbc, String user, String password, boolean register)
      throws ReflectiveOperationException, SQLException {
    Constructor<?> legacyConstructor = connectionClass.getConstructor();

    Properties info = new Properties();
    if (user != null) {
      info.put("user", user);
    }

    if (password != null) {
      info.put("password", password);
    }

    Object driver = legacyConstructor.newInstance();

    DriverManager.deregisterDriver((Driver) driver);
    if (register) {
      DriverManager.registerDriver((Driver) driver);
    }

    Method connect = connectionClass.getDeclaredMethod("connect", String.class, Properties.class);
    connect.setAccessible(true);
    return (Connection) connect.invoke(driver, jdbc, info);
  }

  /** Functional interface for database connection logic. */
  public interface DatabaseConnector {
    /**
     * Establishes database connection.
     *
     * @param classLoader Class loader for JDBC driver
     * @param dir Data directory path
     * @param jdbc JDBC connection string
     * @param user Database username
     * @param password Database password
     * @return Database connection
     * @throws SQLException if connection fails
     */
    Connection connect(ClassLoader classLoader, Path dir, String jdbc, String user, String password)
        throws ReflectiveOperationException, SQLException, IOException;
  }

  /** Functional interface for generating JDBC connection strings. */
  public interface DatabaseStringGetter {
    /**
     * Generates JDBC connection string.
     *
     * @param dir Data directory path
     * @param hostname Database hostname
     * @param database Database name
     * @return JDBC connection string
     */
    String getJdbcString(Path dir, String hostname, String database);
  }
}
