package net.elytrium.limboauth.migration;

import com.google.common.hash.Hashing;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Enum representing various password hashing algorithms used for migrating player data from other
 * authentication plugins to LimboAuth's BCrypt. Each enum constant provides a verifier to check a
 * given password against an old hash format.
 */
@SuppressWarnings("unused")
public enum MigrationHash {

  /** AuthMe's default SHA256: $SHA$salt$hash = SHA256(SHA256(password) + salt) */
  AUTHME(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$salt$hash
        return args.length == 4
            && args[3].equals(getDigest(getDigest(password, "SHA-256") + args[2], "SHA-256"));
      }),
  /** AuthMe variant without leading $: SHA$salt$hash = SHA256(SHA256(password) + salt) */
  AUTHME_NP(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // SHA$salt$hash
        return args.length == 3
            && args[2].equals(getDigest(getDigest(password, "SHA-256") + args[1], "SHA-256"));
      }),
  /** Argon2 hashing algorithm. Uses a dedicated verifier. */
  ARGON2(new Argon2Verifier()),
  /** DBA/JPremium SHA512: SHA$salt$hash = SHA512(SHA512(password) + salt) */
  SHA512_DBA(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // SHA$salt$hash
        return args.length == 3
            && args[2].equals(getDigest(getDigest(password, "SHA-512") + args[1], "SHA-512"));
      }),
  /** SHA512 with salt, no prefix $: SHA$salt$hash = SHA512(password + salt) */
  SHA512_NP(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // SHA$salt$hash
        return args.length == 3 && args[2].equals(getDigest(password + args[1], "SHA-512"));
      }),
  /** SHA512 with salt and prefix $: $SHA$salt$hash = SHA512(password + salt) */
  SHA512_P(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$salt$hash
        return args.length == 4 && args[3].equals(getDigest(password + args[2], "SHA-512"));
      }),
  /** SHA256 with salt, no prefix $: SHA$salt$hash = SHA256(password + salt) */
  SHA256_NP(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // SHA$salt$hash
        return args.length == 3 && args[2].equals(getDigest(password + args[1], "SHA-256"));
      }),
  /** SHA256 with salt and prefix $: $SHA$salt$hash = SHA256(password + salt) */
  SHA256_P(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$salt$hash
        return args.length == 4 && args[3].equals(getDigest(password + args[2], "SHA-256"));
      }),
  /** Basic MD5 hash. */
  MD5((hash, password) -> hash.equals(getDigest(password, "MD5"))),
  /** Moon SHA256: $SHA$hash = SHA256(SHA256(password)) (no salt in hash string) */
  MOON_SHA256(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$hash
        return args.length == 3
            && args[2].equals(getDigest(getDigest(password, "SHA-256"), "SHA-256"));
      }),
  /** NexAuth SHA256: $SHA$hash = SHA256(password) (no salt in hash string) */
  SHA256_NO_SALT(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$hash
        return args.length == 3 && args[2].equals(getDigest(password, "SHA-256"));
      }),
  /** NexAuth SHA512: $SHA$hash = SHA512(password) (no salt in hash string) */
  SHA512_NO_SALT(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$hash
        return args.length == 3 && args[2].equals(getDigest(password, "SHA-512"));
      }),
  /** nLogin variant: $SHA$hash$salt = SHA512(password + salt) */
  SHA512_P_REVERSED_HASH(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$hash$salt
        return args.length == 4 && args[2].equals(getDigest(password + args[3], "SHA-512"));
      }),
  /** nLogin default: $SHA$hash$salt = SHA512(SHA512(password) + salt) */
  SHA512_NLOGIN(
      (hash, password) -> {
        String[] args = hash.split("\\$"); // $SHA$hash$salt
        return args.length == 4
            && args[2].equals(getDigest(getDigest(password, "SHA-512") + args[3], "SHA-512"));
      }),
  /** Basic CRC32C hash. */
  @SuppressWarnings("UnstableApiUsage")
  CRC32C(
      (hash, password) ->
          hash.equals(Hashing.crc32c().hashString(password, StandardCharsets.UTF_8).toString())),
  /** Plaintext password comparison (no hashing). */
  PLAINTEXT(String::equals);

  private final MigrationHashVerifier verifier;

  /**
   * Constructs a MigrationHash enum constant.
   *
   * @param verifier The {@link MigrationHashVerifier} used to check passwords against this hash
   *     type.
   */
  MigrationHash(MigrationHashVerifier verifier) {
    this.verifier = verifier;
  }

  /**
   * Checks if the given plaintext password matches the provided hash string, according to the
   * specific algorithm of this enum constant.
   *
   * @param hash The stored password hash from the old system.
   * @param password The plaintext password to verify.
   * @return {@code true} if the password matches the hash, {@code false} otherwise.
   */
  public boolean checkPassword(String hash, String password) {
    return this.verifier.checkPassword(hash, password);
  }

  private static String getDigest(String string, String algorithm) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
      messageDigest.update(string.getBytes(StandardCharsets.UTF_8));
      byte[] array = messageDigest.digest();
      return Hex.encodeHexString(array);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /** Verifier for Argon2 hashes. */
  private static class Argon2Verifier implements MigrationHashVerifier {

    @MonotonicNonNull private Argon2 argon2;

    @Override
    public boolean checkPassword(String hash, String password) {
      if (this.argon2 == null) {
        this.argon2 = Argon2Factory.create();
      }
      // Argon2 library expects password as char[] or byte[]
      return this.argon2.verify(hash, password.getBytes(StandardCharsets.UTF_8));
    }
  }
}
