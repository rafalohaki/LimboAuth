package net.elytrium.limboauth.migration;

/**
 * Functional interface for verifying a password against a given hash string. This is used by the
 * {@link MigrationHash} enum to delegate password checking for different hashing algorithms.
 */
@FunctionalInterface
public interface MigrationHashVerifier {

  /**
   * Checks if the provided plaintext password matches the given hash.
   *
   * @param hash The stored password hash.
   * @param password The plaintext password to verify.
   * @return {@code true} if the password matches the hash, {@code false} otherwise.
   */
  boolean checkPassword(String hash, String password);
}
