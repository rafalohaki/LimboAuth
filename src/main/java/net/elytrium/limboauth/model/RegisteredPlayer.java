package net.elytrium.limboauth.model;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import com.velocitypowered.api.proxy.Player;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;
import net.elytrium.limboauth.Settings;

/**
 * Represents a player registered with LimboAuth, mapped to a database table. This class contains
 * all persistent data for a player, such as their nickname, password hash, IP addresses, UUIDs, and
 * timestamps.
 */
@DatabaseTable(tableName = "AUTH")
public class RegisteredPlayer {

  /** Database field name for player nickname. */
  /** Database field name for player nickname. */
  public static final String NICKNAME_FIELD = "NICKNAME";

  /** Database field name for lowercase nickname. */
  /** Database field name for lowercase nickname. */
  public static final String LOWERCASE_NICKNAME_FIELD = "LOWERCASENICKNAME";

  /** Database field name for password hash. */
  /** Database field name for password hash. */
  public static final String HASH_FIELD = "HASH";

  /** Database field name for registration IP. */
  /** Database field name for registration IP. */
  public static final String IP_FIELD = "IP";

  /** Database field name for last login IP. */
  /** Database field name for last login IP. */
  public static final String LOGIN_IP_FIELD = "LOGINIP";

  /** Database field name for TOTP token. */
  /** Database field name for TOTP token. */
  public static final String TOTP_TOKEN_FIELD = "TOTPTOKEN";

  /** Database field name for registration date. */
  /** Database field name for registration date. */
  public static final String REG_DATE_FIELD = "REGDATE";

  /** Database field name for last login date. */
  /** Database field name for last login date. */
  public static final String LOGIN_DATE_FIELD = "LOGINDATE";

  /** Database field name for player UUID. */
  /** Database field name for player UUID. */
  public static final String UUID_FIELD = "UUID";

  /** Database field name for premium UUID. */
  /** Database field name for premium UUID. */
  public static final String PREMIUM_UUID_FIELD = "PREMIUMUUID";

  /** Database field name for token issued time. */
  /** Database field name for token issued time. */
  public static final String TOKEN_ISSUED_AT_FIELD = "ISSUEDTIME";

  private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();

  @DatabaseField(canBeNull = false, columnName = NICKNAME_FIELD)
  private String nickname;

  @DatabaseField(id = true, columnName = LOWERCASE_NICKNAME_FIELD)
  private String lowercaseNickname;

  @DatabaseField(canBeNull = false, columnName = HASH_FIELD)
  private String hash = "";

  @DatabaseField(columnName = IP_FIELD, index = true)
  private String ip;

  @DatabaseField(columnName = TOTP_TOKEN_FIELD)
  private String totpToken = "";

  @DatabaseField(columnName = REG_DATE_FIELD)
  private Long regDate = System.currentTimeMillis();

  @DatabaseField(columnName = UUID_FIELD)
  private String uuid = "";

  @DatabaseField(columnName = RegisteredPlayer.PREMIUM_UUID_FIELD, index = true)
  private String premiumUuid = "";

  @DatabaseField(columnName = LOGIN_IP_FIELD)
  private String loginIp;

  @DatabaseField(columnName = LOGIN_DATE_FIELD)
  private Long loginDate = System.currentTimeMillis();

  @DatabaseField(columnName = TOKEN_ISSUED_AT_FIELD)
  private Long tokenIssuedAt = System.currentTimeMillis();

  /**
   * Deprecated constructor for RegisteredPlayer. Use other constructors for creating new instances.
   *
   * @param nickname Player's nickname.
   * @param lowercaseNickname Player's nickname in lowercase (primary key).
   * @param hash Hashed password.
   * @param ip Registration IP address.
   * @param totpToken TOTP secret token.
   * @param regDate Registration timestamp.
   * @param uuid Player's UUID.
   * @param premiumUuid Player's premium (online-mode) UUID.
   * @param loginIp Last login IP address.
   * @param loginDate Last login timestamp.
   */
  @Deprecated
  public RegisteredPlayer(
      String nickname,
      String lowercaseNickname,
      String hash,
      String ip,
      String totpToken,
      Long regDate,
      String uuid,
      String premiumUuid,
      String loginIp,
      Long loginDate) {
    this.nickname = nickname;
    this.lowercaseNickname = lowercaseNickname;
    this.hash = hash;
    this.ip = ip;
    this.totpToken = totpToken;
    this.regDate = regDate;
    this.uuid = uuid;
    this.premiumUuid = premiumUuid;
    this.loginIp = loginIp;
    this.loginDate = loginDate;
  }

  /**
   * Constructs a RegisteredPlayer instance from a Velocity {@link Player} object.
   *
   * @param player The Velocity Player.
   */
  /** Default constructor. */
  public RegisteredPlayer(Player player) {
    this(player.getUsername(), player.getUniqueId(), player.getRemoteAddress());
  }

  /**
   * Constructs a RegisteredPlayer instance with nickname, UUID, and IP address.
   *
   * @param nickname The player's nickname.
   * @param uuid The player's UUID.
   * @param ip The player's IP address.
   */
  /** Default constructor. */
  public RegisteredPlayer(String nickname, UUID uuid, InetSocketAddress ip) {
    this(nickname, uuid.toString(), ip.getAddress().getHostAddress());
  }

  /**
   * Constructs a RegisteredPlayer instance with nickname, UUID string, and IP string.
   *
   * @param nickname The player's nickname.
   * @param uuid The player's UUID as a string.
   * @param ip The player's IP address as a string.
   */
  /** Default constructor. */
  public RegisteredPlayer(String nickname, String uuid, String ip) {
    this.nickname = nickname;
    this.lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    this.uuid = uuid;
    this.ip = ip;
    this.loginIp = ip;
  }

  /** Default constructor for ORMLite. */
  public RegisteredPlayer() {}

  /**
   * Generates a BCrypt hash for the given password using the configured cost factor.
   *
   * @param password The plaintext password.
   * @return The BCrypt hashed password string.
   */
  public static String genHash(String password) {
    return HASHER.hashToString(Settings.IMP.MAIN.BCRYPT_COST, password.toCharArray());
  }

  /**
   * Sets the player's nickname and updates the lowercase nickname.
   *
   * @param nickname The new nickname.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setNickname(String nickname) {
    this.nickname = nickname;
    this.lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    return this;
  }

  /**
   * @return The player's nickname.
   */
  public String getNickname() {
    return this.nickname == null ? this.lowercaseNickname : this.nickname;
  }

  /**
   * @return The player's nickname in lowercase.
   */
  public String getLowercaseNickname() {
    return this.lowercaseNickname;
  }

  /**
   * Sets the player's password by hashing the given plaintext password. Also updates the token
   * issued timestamp.
   *
   * @param password The plaintext password.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setPassword(String password) {
    this.hash = genHash(password);
    this.tokenIssuedAt = System.currentTimeMillis();
    return this;
  }

  /**
   * Sets the player's password hash directly. Also updates the token issued timestamp.
   *
   * @param hash The pre-computed password hash.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setHash(String hash) {
    this.hash = hash;
    this.tokenIssuedAt = System.currentTimeMillis();
    return this;
  }

  /**
   * @return The player's password hash, or an empty string if null.
   */
  public String getHash() {
    return this.hash == null ? "" : this.hash;
  }

  /**
   * Sets the player's registration IP address.
   *
   * @param ip The IP address.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setIP(String ip) {
    this.ip = ip;
    return this;
  }

  /**
   * @return The player's registration IP address, or an empty string if null.
   */
  public String getIP() {
    return this.ip == null ? "" : this.ip;
  }

  /**
   * Sets the player's TOTP secret token.
   *
   * @param totpToken The TOTP secret.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setTotpToken(String totpToken) {
    this.totpToken = totpToken;
    return this;
  }

  /**
   * @return The player's TOTP secret token, or an empty string if null.
   */
  public String getTotpToken() {
    return this.totpToken == null ? "" : this.totpToken;
  }

  /**
   * Sets the player's registration timestamp.
   *
   * @param regDate The registration timestamp.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setRegDate(Long regDate) {
    this.regDate = regDate;
    return this;
  }

  /**
   * @return The player's registration timestamp, or {@link Long#MIN_VALUE} if null.
   */
  public long getRegDate() {
    return this.regDate == null ? Long.MIN_VALUE : this.regDate;
  }

  /**
   * Sets the player's primary UUID (often offline mode UUID or last used).
   *
   * @param uuid The UUID string.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setUuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  /**
   * @return The player's primary UUID string, or an empty string if null.
   */
  public String getUuid() {
    return this.uuid == null ? "" : this.uuid;
  }

  /**
   * Sets the player's premium (online-mode) UUID.
   *
   * @param premiumUuid The premium UUID string.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setPremiumUuid(String premiumUuid) {
    this.premiumUuid = premiumUuid;
    return this;
  }

  /**
   * Sets the player's premium (online-mode) UUID.
   *
   * @param premiumUuid The premium UUID object.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setPremiumUuid(UUID premiumUuid) {
    this.premiumUuid = premiumUuid.toString();
    return this;
  }

  /**
   * @return The player's premium UUID string, or an empty string if null.
   */
  public String getPremiumUuid() {
    return this.premiumUuid == null ? "" : this.premiumUuid;
  }

  /**
   * @return The player's last login IP address, or an empty string if null.
   */
  public String getLoginIp() {
    return this.loginIp == null ? "" : this.loginIp;
  }

  /**
   * Sets the player's last login IP address.
   *
   * @param loginIp The last login IP.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setLoginIp(String loginIp) {
    this.loginIp = loginIp;
    return this;
  }

  /**
   * @return The player's last login timestamp, or {@link Long#MIN_VALUE} if null.
   */
  public long getLoginDate() {
    return this.loginDate == null ? Long.MIN_VALUE : this.loginDate;
  }

  /**
   * Sets the player's last login timestamp.
   *
   * @param loginDate The last login timestamp.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setLoginDate(Long loginDate) {
    this.loginDate = loginDate;
    return this;
  }

  /**
   * @return The timestamp when the current password hash or TOTP token was issued, or {@link
   *     Long#MIN_VALUE} if null.
   */
  public long getTokenIssuedAt() {
    return this.tokenIssuedAt == null ? Long.MIN_VALUE : this.tokenIssuedAt;
  }

  /**
   * Sets the timestamp for when the current password hash or TOTP token was issued.
   *
   * @param tokenIssuedAt The token issued timestamp.
   * @return This RegisteredPlayer instance for chaining.
   */
  public RegisteredPlayer setTokenIssuedAt(Long tokenIssuedAt) {
    this.tokenIssuedAt = tokenIssuedAt;
    return this;
  }
}
