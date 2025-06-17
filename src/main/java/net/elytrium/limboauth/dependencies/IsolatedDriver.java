package net.elytrium.limboauth.dependencies;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A wrapper around a standard JDBC {@link Driver} that allows it to be registered with a custom URL
 * prefix. This enables the use of multiple versions or types of JDBC drivers that might otherwise
 * conflict if registered directly with {@link java.sql.DriverManager}. The {@code IsolatedDriver}
 * delegates calls to the "original" driver after stripping its custom URL prefix.
 */
public class IsolatedDriver implements Driver {

  private final String initializer;
  private Driver original;

  /**
   * Constructs an IsolatedDriver with a specific URL initializer prefix.
   *
   * @param initializer The custom URL prefix (e.g., "jdbc:limboauth_h2:"). Connection URLs starting
   *     with this prefix will be handled by this driver.
   */
  /** Default constructor. */
  public IsolatedDriver(String initializer) {
    this.initializer = initializer;
  }

  /**
   * Gets the URL initializer prefix for this driver.
   *
   * @return The initializer string.
   */
  public String getInitializer() {
    return this.initializer;
  }

  /**
   * Gets the original, wrapped JDBC driver.
   *
   * @return The original {@link Driver} instance.
   */
  public Driver getOriginal() {
    return this.original;
  }

  /**
   * Sets the original JDBC driver to be wrapped.
   *
   * @param driver The {@link Driver} to wrap.
   */
  public void setOriginal(Driver driver) {
    this.original = driver;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (url.startsWith(this.initializer)) {
      return this.original.connect(url.substring(this.initializer.length()), info);
    }
    return null;
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    if (url.startsWith(this.initializer)) {
      if (this.original == null) {
        return false;
      }
      return this.original.acceptsURL(url.substring(this.initializer.length()));
    }
    return false;
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    if (url.startsWith(this.initializer)) {
      return this.original.getPropertyInfo(url.substring(this.initializer.length()), info);
    }
    return new DriverPropertyInfo[0];
  }

  @Override
  public int getMajorVersion() {
    return this.original.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return this.original.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return this.original.jdbcCompliant();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return this.original.getParentLogger();
  }
}
