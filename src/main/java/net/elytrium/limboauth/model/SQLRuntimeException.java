package net.elytrium.limboauth.model;

/**
 * A custom {@link RuntimeException} used to wrap SQLExceptions and other database-related errors
 * occurring within LimboAuth. This allows for more specific error handling and统一 (unified)
 * reporting of database issues.
 */
public class SQLRuntimeException extends RuntimeException {

  /**
   * Constructs a new SQLRuntimeException with a default message and the specified cause.
   *
   * @param cause The underlying cause of this exception.
   */
  /** Default constructor. */
  public SQLRuntimeException(Throwable cause) {
    this("An unexpected internal error was caught during the database SQL operations.", cause);
  }

  /**
   * Constructs a new SQLRuntimeException with the specified detail message and cause.
   *
   * @param message The detail message.
   * @param cause The underlying cause of this exception.
   */
  /** Default constructor. */
  public SQLRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
