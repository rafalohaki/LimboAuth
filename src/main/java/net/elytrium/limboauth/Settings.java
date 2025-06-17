package net.elytrium.limboauth;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.commons.config.ConfigSerializer;
import net.elytrium.commons.config.YamlConfig;
import net.elytrium.commons.kyori.serialization.Serializers;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limboauth.command.CommandPermissionState;
import net.elytrium.limboauth.dependencies.DatabaseLibrary;
import net.elytrium.limboauth.migration.MigrationHash;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

/**
 * Represents the plugin's configuration settings, loaded from a YAML file. This class extends
 * {@link YamlConfig} for automatic loading and saving.
 */
/**
 * Main configuration class for LimboAuth this.plugin. Contains all configurable settings loaded
 * from YAML.
 */
public class Settings extends YamlConfig {

  /** Singleton instance for easy access to default/current settings. */
  @Ignore public static final Settings IMP = new Settings();

  /** Current version of the LimboAuth this.plugin. */
  @Final public String VERSION = BuildConstants.AUTH_VERSION;

  /**
   * Configures the text serializer to be used for messages.
   *
   * <ul>
   *   <li>LEGACY_AMPERSAND: {@code "&c&lExample &c&9Text"}
   *   <li>LEGACY_SECTION: {@code "§c§lExample §c§9Text"}
   *   <li>MINIMESSAGE: {@code "<bold><red>Example</red> <blue>Text</blue></bold>"} (see <a
   *       href="https://webui.adventure.kyori.net/">MiniMessage Web UI</a>)
   *   <li>GSON: {@code "[{\"text\":\"Example\",\"bold\":true,\"color\":\"red\"},...]"} (see <a
   *       href="https://minecraft.tools/en/json_text.php/">JSON Text Generator</a>)
   *   <li>GSON_COLOR_DOWNSAMPLING: Same as GSON, but uses downsampling.
   * </ul>
   */
  @Comment({
    "Available serializers:",
    "LEGACY_AMPERSAND - \"&c&lExample &c&9Text\".",
    "LEGACY_SECTION - \"§c§lExample §c§9Text\".",
    "MINIMESSAGE - \"<bold><red>Example</red> <blue>Text</blue></bold>\". (https://webui.adventure.kyori.net/)",
    "GSON - \"[{\"text\":\"Example\",\"bold\":true,\"color\":\"red\"},{\"text\":\" \",\"bold\":true},{\"text\":\"Text\",\"bold\":true,\"color\":\"blue\"}]\". (https://minecraft.tools/en/json_text.php/)",
    "GSON_COLOR_DOWNSAMPLING - Same as GSON, but uses downsampling."
  })
  public Serializers SERIALIZER = Serializers.LEGACY_AMPERSAND;

  /** The prefix used for plugin messages. */
  public String PREFIX = "LimboAuth &6>>&f";

  /** Main configuration section. */
  @Create public MAIN MAIN;

  /** Main configuration settings. Use {NL} for new line and {PRFX} for prefix in messages. */
  /** Main plugin configuration settings. */
  /** Main plugin configuration settings. */
  public static class MAIN {

    /** Maximum time for player to authenticate in milliseconds. */
    @Comment(
        "Maximum time for player to authenticate in milliseconds. If the player stays on the auth limbo for longer than this time, then the player will be kicked.")
    public int AUTH_TIME = 60000;

    /** Whether to enable the boss bar during authentication. */
    public boolean ENABLE_BOSSBAR = true;

    /** Color of the authentication boss bar (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE). */
    @Comment("Available colors: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE")
    public BossBar.Color BOSSBAR_COLOR = BossBar.Color.RED;

    /**
     * Overlay style of the authentication boss bar (PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12,
     * NOTCHED_20).
     */
    @Comment("Available overlays: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20")
    public BossBar.Overlay BOSSBAR_OVERLAY = BossBar.Overlay.NOTCHED_20;

    /** Minimum allowed password length. */
    public int MIN_PASSWORD_LENGTH = 4;

    /** Maximum allowed password length (BCrypt limit is 71). */
    @Comment(
        "Max password length for the BCrypt hashing algorithm, which is used in this plugin, can't be higher than 71. You can set a lower value than 71.")
    public int MAX_PASSWORD_LENGTH = 71;

    /** Whether to check password strength against a list of unsafe passwords. */
    public boolean CHECK_PASSWORD_STRENGTH = true;

    /** Filename for the list of unsafe passwords. */
    public String UNSAFE_PASSWORDS_FILE = "unsafe_passwords.txt";

    /**
     * If true, players with premium usernames must register/authenticate. If false, they must use a
     * premium account.
     */
    @Comment({
      "Players with premium nicknames should register/auth if this option is enabled",
      "Players with premium nicknames must login with a premium Minecraft account if this option is disabled",
    })
    public boolean ONLINE_MODE_NEED_AUTH = true;

    /**
     * Experimental: If false, disables premium auth for unregistered players after one failure,
     * allowing offline use of online names. Requires specific other settings.
     */
    @Comment({
      "WARNING: its experimental feature, so disable only if you really know what you are doing",
      "When enabled, this option will keep default 'online-mode-need-auth' behavior",
      "When disabled, this option will disable premium authentication for unregistered players if they fail it once,",
      "allowing offline-mode players to use online-mode usernames",
      "Does nothing when enabled, but when disabled require 'save-premium-accounts: true', 'online-mode-need-auth: false' and 'purge_premium_cache_millis > 100000'"
    })
    public boolean ONLINE_MODE_NEED_AUTH_STRICT = true;

    /** If false, Floodgate plugin is required for Bedrock players to bypass authentication. */
    @Comment("Needs floodgate plugin if disabled.")
    public boolean FLOODGATE_NEED_AUTH = true;

    /** If true, completely disables hybrid authentication features. */
    @Comment("TOTALLY disables hybrid auth feature")
    public boolean FORCE_OFFLINE_MODE = false;

    /** If true, forces all players to get offline mode UUIDs. */
    @Comment("Forces all players to get offline uuid")
    public boolean FORCE_OFFLINE_UUID = false;

    /** If true, checks local database for premium status before Mojang API. */
    @Comment(
        "If enabled, the plugin will firstly check whether the player is premium through the local database, and secondly through Mojang API.")
    public boolean CHECK_PREMIUM_PRIORITY_INTERNAL = true;

    /** Delay in milliseconds before sending auth-confirming messages/titles. */
    @Comment(
        "Delay in milliseconds before sending auth-confirming titles and messages to the player. (login-premium-title, login-floodgate, etc.)")
    public int PREMIUM_AND_FLOODGATE_MESSAGES_DELAY = 1250;

    /** If true, forcibly sets player's UUID to the value from the database. */
    @Comment({
      "Forcibly set player's UUID to the value from the database",
      "If the player had the cracked account, and switched to the premium account, the cracked UUID will be used."
    })
    public boolean SAVE_UUID = true;

    /**
     * If true, saves accounts of premium users who login via 'online-mode-need-auth: false' in the
     * database.
     */
    @Comment({
      "Saves in the database the accounts of premium users whose login is via online-mode-need-auth: false",
      "Can be disabled to reduce the size of stored data in the database"
    })
    public boolean SAVE_PREMIUM_ACCOUNTS = true;

    /** Whether to enable TOTP (2FA) functionality. */
    public boolean ENABLE_TOTP = true;

    /** If true, enabling TOTP requires the current password. */
    public boolean TOTP_NEED_PASSWORD = true;

    /** If true, registration requires repeating the password. */
    public boolean REGISTER_NEED_REPEAT_PASSWORD = true;

    /** If true, changing password requires the old password. */
    public boolean CHANGE_PASSWORD_NEED_OLD_PASSWORD = true;

    /** Keyword used in unregister and premium commands for confirmation. */
    @Comment("Used in unregister and premium commands.")
    public String CONFIRM_KEYWORD = "confirm";

    /** Prefix added to offline mode player nicknames. */
    @Comment("This prefix will be added to offline mode players nickname")
    public String OFFLINE_MODE_PREFIX = "";

    /** Prefix added to online mode player nicknames. */
    @Comment("This prefix will be added to online mode players nickname")
    public String ONLINE_MODE_PREFIX = "";

    /** Hash algorithm for migrating from other plugins (AUTHME, SHA256_NP, MD5, etc.). */
    @Comment({
      "If you want to migrate your database from another plugin, which is not using BCrypt.",
      "You can set an old hash algorithm to migrate from.",
      "AUTHME - AuthMe SHA256(SHA256(password) + salt) that looks like $SHA$salt$hash (AuthMe, MoonVKAuth, DSKAuth, DBA)",
      "AUTHME_NP - AuthMe SHA256(SHA256(password) + salt) that looks like SHA$salt$hash (JPremium)",
      "SHA256_NP - SHA256(password) that looks like SHA$salt$hash",
      "SHA256_P - SHA256(password) that looks like $SHA$salt$hash",
      "SHA512_NP - SHA512(password) that looks like SHA$salt$hash",
      "SHA512_P - SHA512(password) that looks like $SHA$salt$hash",
      "SHA512_DBA - DBA plugin SHA512(SHA512(password) + salt) that looks like SHA$salt$hash (DBA, JPremium)",
      "MD5 - Basic md5 hash",
      "ARGON2 - Argon2 hash that looks like $argon2i$v=1234$m=1234,t=1234,p=1234$hash",
      "MOON_SHA256 - Moon SHA256(SHA256(password)) that looks like $SHA$hash (no salt)",
      "SHA256_NO_SALT - SHA256(password) that looks like $SHA$hash (NexAuth)",
      "SHA512_NO_SALT - SHA512(password) that looks like $SHA$hash (NexAuth)",
      "SHA512_P_REVERSED_HASH - SHA512(password) that looks like $SHA$hash$salt (nLogin)",
      "SHA512_NLOGIN - SHA512(SHA512(password) + salt) that looks like $SHA$hash$salt (nLogin)",
      "CRC32C - Basic CRC32C hash",
      "PLAINTEXT - Plain text",
    })
    public MigrationHash MIGRATION_HASH = MigrationHash.AUTHME;

    /** Dimension for the authentication Limbo world (OVERWORLD, NETHER, THE_END). */
    @Comment("Available dimensions: OVERWORLD, NETHER, THE_END")
    public Dimension DIMENSION = Dimension.THE_END;

    /** Interval in milliseconds for purging general cache entries. */
    public long PURGE_CACHE_MILLIS = 3600000;

    /** Interval in milliseconds for purging premium status cache entries. */
    public long PURGE_PREMIUM_CACHE_MILLIS = 28800000;

    /** Interval in milliseconds for purging brute-force attempt cache entries. */
    public long PURGE_BRUTEFORCE_CACHE_MILLIS = 28800000;

    /** Maximum incorrect password attempts before an IP is temporarily blocked. */
    @Comment("Used to ban IPs when a possible attacker incorrectly enters the password")
    public int BRUTEFORCE_MAX_ATTEMPTS = 10;

    /** URL for generating QR codes for TOTP, with {data} placeholder. */
    @Comment("QR Generator URL, set {data} placeholder")
    public String QR_GENERATOR_URL =
        "https://api.qrserver.com/v1/create-qr-code/?data={data}&size=200x200&ecc=M&margin=30";

    /** Issuer name displayed in TOTP authenticator apps. */
    public String TOTP_ISSUER = "LimboAuth by Elytrium";

    /** Cost factor for BCrypt password hashing. */
    public int BCRYPT_COST = 10;

    /** Number of allowed login attempts before kicking the player. */
    public int LOGIN_ATTEMPTS = 3;

    /** Maximum number of registrations allowed per IP address. */
    public int IP_LIMIT_REGISTRATIONS = 3;

    /** Time in milliseconds for which the IP registration limit is effective (0 to disable). */
    @Comment("Time in milliseconds, when ip limit works, set to 0 for disable.")
    public long IP_LIMIT_VALID_TIME = 21600000;

    /** Regex for validating allowed player nicknames. */
    @Comment({
      "Regex of allowed nicknames",
      "^ means the start of the line, $ means the end of the line",
      "[A-Za-z0-9_] is a character set of A-Z, a-z, 0-9 and _",
      "{3,16} means that allowed length is from 3 to 16 chars"
    })
    public String ALLOWED_NICKNAME_REGEX = "^[A-Za-z0-9_]{3,16}$";

    /** Whether to load a custom world file for the authentication Limbo. */
    public boolean LOAD_WORLD = false;

    /** Type of the world file to load (SCHEMATIC, STRUCTURE, WORLDEDIT_SCHEM). */
    @Comment({
      "World file type:",
      " SCHEMATIC (MCEdit .schematic, 1.12.2 and lower, not recommended)",
      " STRUCTURE (structure block .nbt, any Minecraft version is supported, but the latest one is recommended).",
      " WORLDEDIT_SCHEM (WorldEdit .schem, any Minecraft version is supported, but the latest one is recommended)."
    })
    public BuiltInWorldFileType WORLD_FILE_TYPE = BuiltInWorldFileType.STRUCTURE;

    /** Path to the world file (relative to plugin data directory). */
    public String WORLD_FILE_PATH = "world.nbt";

    /** Whether to disable fall damage in the authentication Limbo. */
    public boolean DISABLE_FALLING = true;

    /** Time in ticks for the authentication Limbo world (24000 ticks = 1 in-game day). */
    @Comment("World time in ticks (24000 ticks == 1 in-game day)")
    public long WORLD_TICKS = 1000L;

    /** Light level for the authentication Limbo world (0-15). */
    @Comment("World light level (from 0 to 15)")
    public int WORLD_LIGHT_LEVEL = 15;

    /**
     * Game mode for players in the authentication Limbo (ADVENTURE, CREATIVE, SURVIVAL, SPECTATOR).
     */
    @Comment("Available: ADVENTURE, CREATIVE, SURVIVAL, SPECTATOR")
    public GameMode GAME_MODE = GameMode.ADVENTURE;

    /** Name of the Limbo server instance used for authentication. */
    @Comment("Name of the Limbo server instance used for authentication.")
    public String LIMBO_SERVER_NAME = "LimboAuth";

    /** Enable checking for plugin updates on startup. */
    @Comment("Enable checking for plugin updates on startup.")
    public boolean CHECK_FOR_UPDATES = true;

    /** Custom URL for checking if a player username is premium (e.g., Mojang or Cloudflare API). */
    @Comment({
      "Custom isPremium URL",
      "You can use Mojang one's API (set by default)",
      "Or CloudFlare one's: https://api.ashcon.app/mojang/v2/user/%s",
      "Or use this code to make your own API: https://blog.cloudflare.com/minecraft-api-with-workers-coffeescript/",
      "Or implement your own API, it should just respond with HTTP code 200 (see parameters below) only if the player is premium"
    })
    public String ISPREMIUM_AUTH_URL = "https://api.mojang.com/users/profiles/minecraft/%s";

    /** HTTP status codes indicating a user exists from the premium check API. */
    @Comment({
      "Status codes (see the comment above)",
      "Responses with unlisted status codes will be identified as responses with a server error",
      "Set 200 if you use using Mojang or CloudFlare API"
    })
    public List<Integer> STATUS_CODE_USER_EXISTS = List.of(200);

    /** HTTP status codes indicating a user does not exist from the premium check API. */
    @Comment("Set 204 and 404 if you use Mojang API, 404 if you use CloudFlare API")
    public List<Integer> STATUS_CODE_USER_NOT_EXISTS = List.of(204, 404);

    /** HTTP status codes indicating a rate limit from the premium check API. */
    @Comment("Set 429 if you use Mojang or CloudFlare API")
    public List<Integer> STATUS_CODE_RATE_LIMIT = List.of(429);

    /** JSON fields to validate in the response when a user exists (empty to disable). */
    @Comment({
      "Sample Mojang API exists response: {\"name\":\"hevav\",\"id\":\"9c7024b2a48746b3b3934f397ae5d70f\"}",
      "Sample CloudFlare API exists response: {\"uuid\":\"9c7024b2a48746b3b3934f397ae5d70f\",\"username\":\"hevav\", ...}",
      "",
      "Sample Mojang API not exists response (sometimes can be empty): {\"path\":\"/users/profiles/minecraft/someletters1234566\",\"errorMessage\":\"Couldn't find any profile with that name\"}",
      "Sample CloudFlare API not exists response: {\"code\":404,\"error\":\"Not Found\",\"reason\":\"No user with the name 'someletters123456' was found\"}",
      "",
      "Responses with an invalid scheme will be identified as responses with a server error",
      "Set this parameter to [], to disable JSON scheme validation"
    })
    public List<String> USER_EXISTS_JSON_VALIDATOR_FIELDS = List.of("name", "id");

    /** JSON field name containing the UUID in the premium check response. */
    public String JSON_UUID_FIELD = "id";

    /** JSON fields to validate in the response when a user does not exist (empty to disable). */
    public List<String> USER_NOT_EXISTS_JSON_VALIDATOR_FIELDS = List.of();

    /**
     * If true, players are treated as premium if Mojang API is rate-limited. If false, treated as
     * cracked.
     */
    @Comment({
      "If Mojang rate-limits your server, we cannot determine if the player is premium or not",
      "This option allows you to choose whether every player will be defined as premium or as cracked while Mojang is rate-limiting the server",
      "True - as premium; False - as cracked"
    })
    public boolean ON_RATE_LIMIT_PREMIUM = true;

    /**
     * If true, players are treated as premium if Mojang API is down. If false, treated as cracked.
     */
    @Comment({
      "If Mojang API is down, we cannot determine if the player is premium or not",
      "This option allows you to choose whether every player will be defined as premium or as cracked while Mojang API is unavailable",
      "True - as premium; False - as cracked"
    })
    public boolean ON_SERVER_ERROR_PREMIUM = true;

    /** Aliases for the register command. */
    public List<String> REGISTER_COMMAND = List.of("/r", "/reg", "/register");

    /** Aliases for the login command. */
    public List<String> LOGIN_COMMAND = List.of("/l", "/log", "/login");

    /** Aliases for the TOTP (2FA) command. */
    public List<String> TOTP_COMMAND = List.of("/2fa", "/totp");

    /** If true, new player registrations are disabled. */
    @Comment("New players will be kicked with registrations-disabled-kick message")
    public boolean DISABLE_REGISTRATIONS = false;

    /** Number of recovery codes generated for TOTP. */
    public int TOTP_RECOVERY_CODES_AMOUNT = 16;

    /** Settings related to the client-side mod integration. */
    @Create public Settings.MAIN.MOD MOD;

    /** Settings for client mod integration. See https://github.com/Elytrium/LimboAuth-ClientMod */
    @Comment({
      "Implement the automatic login using the plugin, the LimboAuth client mod and optionally using a custom launcher",
      "See https://github.com/Elytrium/LimboAuth-ClientMod"
    })
    public static class MOD {
      /** Whether to enable client mod integration features. */
      public boolean ENABLED = true;

      /** If true, players can only log in if they have the client mod installed. */
      @Comment("Should the plugin forbid logging in without a mod")
      public boolean LOGIN_ONLY_BY_MOD = false;

      /**
       * Verification key (MD5 hashed) for client mod communication, must match server hash issuer.
       */
      @Comment(
          "The key must be the same in the plugin config and in the server hash issuer, if you use it")
      @CustomSerializer(serializerClass = MD5KeySerializer.class)
      public byte[] VERIFY_KEY = null;
    }

    /** Coordinates for loading the custom world schematic/structure. */
    @Create public Settings.MAIN.WORLD_COORDS WORLD_COORDS;

    /** World coordinates for schematic/structure placement. */
    public static class WORLD_COORDS {
      /** X-coordinate for world placement. */
      public int X = 0;

      /** Y-coordinate for world placement. */
      public int Y = 0;

      /** Z-coordinate for world placement. */
      public int Z = 0;
    }

    /** Spawn coordinates for players in the authentication Limbo. */
    @Create public MAIN.AUTH_COORDS AUTH_COORDS;

    /** Authentication Limbo spawn coordinates and orientation. */
    public static class AUTH_COORDS {
      /** Spawn X-coordinate. */
      public double X = 0;

      /** Spawn Y-coordinate. */
      public double Y = 0;

      /** Spawn Z-coordinate. */
      public double Z = 0;

      /** Spawn Yaw (horizontal rotation). */
      public double YAW = 0;

      /** Spawn Pitch (vertical rotation). */
      public double PITCH = 0;
    }

    /** Title settings for cracked (non-premium) player authentication. */
    @Create public Settings.MAIN.CRACKED_TITLE_SETTINGS CRACKED_TITLE_SETTINGS;

    /** Title display settings for cracked player authentication. */
    public static class CRACKED_TITLE_SETTINGS {
      /** Fade-in time in ticks. */
      public int FADE_IN = 10;

      /** Stay time in ticks. */
      public int STAY = 70;

      /** Fade-out time in ticks. */
      public int FADE_OUT = 20;

      /** Whether to clear the title after successful login. */
      public boolean CLEAR_AFTER_LOGIN = false;

      /**
       * @return Title.Times object based on these settings.
       */
      public Title.Times toTimes() {
        return Title.Times.times(
            Ticks.duration(this.FADE_IN), Ticks.duration(this.STAY), Ticks.duration(this.FADE_OUT));
      }
    }

    /** Title settings for premium player auto-login. */
    @Create public Settings.MAIN.PREMIUM_TITLE_SETTINGS PREMIUM_TITLE_SETTINGS;

    /** Title display settings for premium player auto-login. */
    public static class PREMIUM_TITLE_SETTINGS {
      /** Fade-in time in ticks. */
      public int FADE_IN = 10;

      /** Stay time in ticks. */
      public int STAY = 70;

      /** Fade-out time in ticks. */
      public int FADE_OUT = 20;

      /**
       * @return Title.Times object based on these settings.
       */
      public Title.Times toTimes() {
        return Title.Times.times(
            Ticks.duration(this.FADE_IN), Ticks.duration(this.STAY), Ticks.duration(this.FADE_OUT));
      }
    }

    /** Settings for the backend API (e.g., for PlaceholderAPI expansion). */
    @Create public Settings.MAIN.BACKEND_API BACKEND_API;

    /** Backend API settings. */
    public static class BACKEND_API {
      /** Whether the backend API should be enabled. Required for PlaceholderAPI expansion. */
      @Comment({
        "Should backend API be enabled?",
        "Required for PlaceholderAPI expansion to work (https://github.com/UserNugget/LimboAuth-Expansion)"
      })
      public boolean ENABLED = false;

      /** Token for authenticating backend API requests. */
      @Comment("Backend API token")
      public String TOKEN = Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);

      /** List of enabled backend API endpoints. */
      @Comment({
        "Available endpoints:",
        " premium_state, hash, totp_token, login_date, reg_date, token_issued_at,",
        " uuid, premium_uuid, ip, login_ip, token_issued_at"
      })
      public List<String> ENABLED_ENDPOINTS =
          List.of(
              "premium_state", "login_date", "reg_date", "uuid", "premium_uuid", "token_issued_at");
    }

    /** Permission states for various plugin commands. */
    @Create public MAIN.COMMAND_PERMISSION_STATE COMMAND_PERMISSION_STATE;

    /**
     * Defines permission states for commands (FALSE, TRUE, PERMISSION). FALSE: Command disallowed.
     * TRUE: Command allowed if player has false permission state (effectively public). PERMISSION:
     * Command allowed if player has true permission state (requires specific permission node).
     */
    @Comment({
      "Available values: FALSE, TRUE, PERMISSION",
      " FALSE - the command will be disallowed",
      " TRUE - the command will be allowed if player has false permission state",
      " PERMISSION - the command will be allowed if player has true permission state"
    })
    public static class COMMAND_PERMISSION_STATE {
      /** Permission for /changepassword: limboauth.commands.changepassword */
      @Comment("Permission: limboauth.commands.changepassword")
      public CommandPermissionState CHANGE_PASSWORD = CommandPermissionState.PERMISSION;

      /** Permission for /destroysession: limboauth.commands.destroysession */
      @Comment("Permission: limboauth.commands.destroysession")
      public CommandPermissionState DESTROY_SESSION = CommandPermissionState.PERMISSION;

      /** Permission for /premium: limboauth.commands.premium */
      @Comment("Permission: limboauth.commands.premium")
      public CommandPermissionState PREMIUM = CommandPermissionState.PERMISSION;

      /** Permission for /totp: limboauth.commands.totp */
      @Comment("Permission: limboauth.commands.totp")
      public CommandPermissionState TOTP = CommandPermissionState.PERMISSION;

      /** Permission for /unregister: limboauth.commands.unregister */
      @Comment("Permission: limboauth.commands.unregister")
      public CommandPermissionState UNREGISTER = CommandPermissionState.PERMISSION;

      /** Permission for /forcechangepassword: limboauth.admin.forcechangepassword */
      @Comment("Permission: limboauth.admin.forcechangepassword")
      public CommandPermissionState FORCE_CHANGE_PASSWORD = CommandPermissionState.PERMISSION;

      /** Permission for /forceregister: limboauth.admin.forceregister */
      @Comment("Permission: limboauth.admin.forceregister")
      public CommandPermissionState FORCE_REGISTER = CommandPermissionState.PERMISSION;

      /** Permission for /forcelogin: limboauth.admin.forcelogin */
      @Comment("Permission: limboauth.admin.forcelogin")
      public CommandPermissionState FORCE_LOGIN = CommandPermissionState.PERMISSION;

      /** Permission for /forceunregister: limboauth.admin.forceunregister */
      @Comment("Permission: limboauth.admin.forceunregister")
      public CommandPermissionState FORCE_UNREGISTER = CommandPermissionState.PERMISSION;

      /** Permission for /limboauth reload: limboauth.admin.reload */
      @Comment("Permission: limboauth.admin.reload")
      public CommandPermissionState RELOAD = CommandPermissionState.PERMISSION;

      /** Permission for /limboauth help: limboauth.admin.help (or general help access) */
      @Comment("Permission: limboauth.admin.help")
      public CommandPermissionState HELP = CommandPermissionState.TRUE;
    }

    /** Localized strings used by the this.plugin. */
    @Create public MAIN.STRINGS STRINGS;

    /**
     * Contains all translatable messages used by the this.plugin. Use {PRFX} for prefix and {NL}
     * for newline. Placeholders like {0}, {1} are replaced by command arguments or context.
     */
    public static class STRINGS {
      public String RELOAD = "{PRFX} &aReloaded successfully!";
      public String ERROR_OCCURRED = "{PRFX} &cAn internal error has occurred!";
      public String RATELIMITED = "{PRFX} &cPlease wait before next usage!";
      public String DATABASE_ERROR_KICK = "{PRFX} &cA database error has occurred!";
      public String NOT_PLAYER = "{PRFX} &cConsole is not allowed to execute this command!";
      public String NOT_REGISTERED =
          "{PRFX} &cYou are not registered or your account is &6PREMIUM!";
      public String CRACKED_COMMAND =
          "{PRFX}{NL}&aYou can not use this command since your account is &6PREMIUM&a!";
      public String WRONG_PASSWORD = "{PRFX} &cPassword is wrong!";
      public String PASSWORD_SAME_AS_OLD =
          "{PRFX} &cYour new password cannot be the same as your old password.";
      public String NICKNAME_INVALID_KICK =
          "{PRFX}{NL}&cYour nickname contains forbidden characters. Please, change your nickname!";
      public String RECONNECT_KICK = "{PRFX}{NL}&cReconnect to the server to verify your account!";

      @Comment("6 hours by default in ip-limit-valid-time")
      public String IP_LIMIT_KICK =
          "{PRFX}{NL}{NL}&cYour IP has reached max registered accounts. If this is an error, restart your router, or wait about 6 hours.";

      public String WRONG_NICKNAME_CASE_KICK =
          "{PRFX}{NL}&cYou should join using username &6{0}&c, not &6{1}&c.";
      public String BOSSBAR = "{PRFX} You have &6{0} &fseconds left to log in.";
      public String TIMES_UP = "{PRFX}{NL}&cAuthorization time is up.";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_PREMIUM =
          "{PRFX} You've been logged in automatically using the premium account!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_PREMIUM_TITLE = "{PRFX} Welcome!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_PREMIUM_SUBTITLE = "&aYou have been logged in as premium player!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_FLOODGATE =
          "{PRFX} You've been logged in automatically using the bedrock account!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_FLOODGATE_TITLE = "{PRFX} Welcome!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_FLOODGATE_SUBTITLE = "&aYou have been logged in as bedrock player!";

      public String LOGIN =
          "{PRFX} &aPlease, login using &6/login <password>&a, you have &6{0} &aattempts.";
      public String LOGIN_WRONG_PASSWORD =
          "{PRFX} &cYou've entered the wrong password, you have &6{0} &cattempts left.";
      public String LOGIN_WRONG_PASSWORD_KICK =
          "{PRFX}{NL}&cYou've entered the wrong password numerous times!";
      public String LOGIN_SUCCESSFUL = "{PRFX} &aSuccessfully logged in!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_TITLE = "&fPlease, login using &6/login <password>&a.";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_SUBTITLE = "&aYou have &6{0} &aattempts.";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_SUCCESSFUL_TITLE = "{PRFX}";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String LOGIN_SUCCESSFUL_SUBTITLE = "&aSuccessfully logged in!";

      @Comment(
          "Or if register-need-repeat-password set to false remove the \"<repeat password>\" part.")
      public String REGISTER =
          "{PRFX} Please, register using &6/register <password> <repeat password>";

      public String REGISTER_DIFFERENT_PASSWORDS =
          "{PRFX} &cThe entered passwords differ from each other!";
      public String REGISTER_PASSWORD_TOO_SHORT =
          "{PRFX} &cYou entered a too short password, use a different one!";
      public String REGISTER_PASSWORD_TOO_LONG =
          "{PRFX} &cYou entered a too long password, use a different one!";
      public String REGISTER_PASSWORD_UNSAFE =
          "{PRFX} &cYour password is unsafe, use a different one!";
      public String REGISTER_SUCCESSFUL = "{PRFX} &aSuccessfully registered!";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String REGISTER_TITLE = "{PRFX}";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String REGISTER_SUBTITLE =
          "&aPlease, register using &6/register <password> <repeat password>";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String REGISTER_SUCCESSFUL_TITLE = "{PRFX}";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String REGISTER_SUCCESSFUL_SUBTITLE = "&aSuccessfully registered!";

      public String UNREGISTER_SUCCESSFUL = "{PRFX}{NL}&aSuccessfully unregistered!";
      public String UNREGISTER_USAGE = "{PRFX} Usage: &6/unregister <current password> confirm";
      public String PREMIUM_SUCCESSFUL =
          "{PRFX}{NL}&aSuccessfully changed account state to &6PREMIUM&a!";
      public String ALREADY_PREMIUM = "{PRFX} &cYour account is already &6PREMIUM&c!";
      public String NOT_PREMIUM = "{PRFX} &cYour account is not &6PREMIUM&c!";
      public String PREMIUM_USAGE = "{PRFX} Usage: &6/premium <current password> confirm";
      public String EVENT_CANCELLED = "{PRFX} Authorization event was cancelled";
      public String FORCE_UNREGISTER_SUCCESSFUL = "{PRFX} &6{0} &asuccessfully unregistered!";
      public String FORCE_UNREGISTER_KICK =
          "{PRFX}{NL}&aYou have been unregistered by an administrator!";
      public String FORCE_UNREGISTER_NOT_SUCCESSFUL =
          "{PRFX} &cUnable to unregister &6{0}&c. Most likely this player has never been on this this.server.";
      public String FORCE_UNREGISTER_USAGE = "{PRFX} Usage: &6/forceunregister <nickname>";
      public String REGISTRATIONS_DISABLED_KICK = "{PRFX} Registrations are currently disabled.";
      public String CHANGE_PASSWORD_SUCCESSFUL = "{PRFX} &aSuccessfully changed password!";

      @Comment(
          "Or if change-password-need-old-pass set to false remove the \"<old password>\" part.")
      public String CHANGE_PASSWORD_USAGE =
          "{PRFX} Usage: &6/changepassword <old password> <new password>";

      public String FORCE_CHANGE_PASSWORD_SUCCESSFUL =
          "{PRFX} &aSuccessfully changed password for player &6{0}&a!";
      public String FORCE_CHANGE_PASSWORD_MESSAGE =
          "{PRFX} &aYour password has been changed to &6{0} &aby an administator!";
      public String FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL =
          "{PRFX} &cUnable to change password for &6{0}&c. Most likely this player has never been on this this.server.";
      public String FORCE_CHANGE_PASSWORD_NOT_REGISTERED =
          "{PRFX} &cPlayer &6{0}&c is not registered.";
      public String PLAYER_IS_PREMIUM_NO_PASS_CHANGE =
          "{PRFX} &cPlayer &6{0}&c is a premium account and does not use a password here.";
      public String FORCE_CHANGE_PASSWORD_USAGE =
          "{PRFX} Usage: &6/forcechangepassword <nickname> <new password>";
      public String FORCE_REGISTER_USAGE = "{PRFX} Usage: &6/forceregister <nickname> <password>";
      public String FORCE_REGISTER_INCORRECT_NICKNAME =
          "{PRFX} &cNickname contains forbidden characters.";
      public String FORCE_REGISTER_TAKEN_NICKNAME = "{PRFX} &cThis nickname is already taken.";
      public String FORCE_REGISTER_SUCCESSFUL = "{PRFX} &aSuccessfully registered player &6{0}&a!";
      public String FORCE_REGISTER_NOT_SUCCESSFUL = "{PRFX} &cUnable to register player &6{0}&c.";
      public String FORCE_LOGIN_USAGE = "{PRFX} Usage: &6/forcelogin <nickname>";
      public String FORCE_LOGIN_SUCCESSFUL = "{PRFX} &aSuccessfully authenticated &6{0}&a!";
      public String FORCE_LOGIN_UNKNOWN_PLAYER =
          "{PRFX} &cUnable to find authenticating player with username &6{0}&a!";
      public String TOTP = "{PRFX} Please, enter your 2FA key using &6/2fa <key>";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String TOTP_TITLE = "{PRFX}";

      /** Can be empty. */
      @Comment(value = "Can be empty.", at = Comment.At.SAME_LINE)
      public String TOTP_SUBTITLE = "&aEnter your 2FA key using &6/2fa <key>";

      public String TOTP_SUCCESSFUL = "{PRFX} &aSuccessfully enabled 2FA!";
      public String TOTP_DISABLED = "{PRFX} &aSuccessfully disabled 2FA!";
      public String TOTP_NOT_ENABLED =
          "{PRFX} &cTwo-factor authentication is not enabled for your account.";

      @Comment("Or if totp-need-pass set to false remove the \"<current password>\" part.")
      public String TOTP_USAGE =
          "{PRFX} Usage: &6/2fa enable <current password>&f or &6/2fa disable <totp key>&f.";

      public String TOTP_WRONG = "{PRFX} &cWrong 2FA key!";
      public String TOTP_ALREADY_ENABLED =
          "{PRFX} &c2FA is already enabled. Disable it using &6/2fa disable <key>&c.";
      public String TOTP_QR = "{PRFX} Click here to open 2FA QR code in browser.";
      public String TOTP_TOKEN = "{PRFX} &aYour 2FA token &7(Click to copy)&a: &6{0}";
      public String TOTP_RECOVERY = "{PRFX} &aYour recovery codes &7(Click to copy)&a: &6{0}";
      public String DESTROY_SESSION_SUCCESSFUL =
          "{PRFX} &eYour session is now destroyed, you'll need to log in again after reconnecting.";
      public String MOD_SESSION_EXPIRED = "{PRFX} Your session has expired, log in again.";
      public String MOJANG_API_RATE_LIMITED =
          "{PRFX} &cMojang API is currently rate limited. Please try again later.";
      public String MOJANG_API_ERROR =
          "{PRFX} &cCould not verify premium status due to an API error.";
    }
  }

  /** Database configuration section. */
  @Create public DATABASE DATABASE;

  /** Database connection settings. */
  @Comment("Database settings")
  /** Database configuration settings. */
  /** Database configuration settings. */
  public static class DATABASE {
    /** Database type (mariadb, mysql, postgresql, sqlite, h2). */
    @Comment("Database type: mariadb, mysql, postgresql, sqlite or h2.")
    public DatabaseLibrary STORAGE_TYPE = DatabaseLibrary.H2;

    /** Hostname and port for network-based databases (e.g., 127.0.0.1:3306). */
    @Comment("Settings for Network-based database (like MySQL, PostgreSQL): ")
    public String HOSTNAME = "127.0.0.1:3306";

    /** Username for database connection. */
    public String USER = "user";

    /** Password for database connection. */
    public String PASSWORD = "password";

    /** Name of the database/schema. */
    public String DATABASE = "limboauth";

    /** Additional JDBC connection parameters (e.g., ?autoReconnect=true). */
    public String CONNECTION_PARAMETERS = "?autoReconnect=true&initialTimeout=1&useSSL=false";
  }

  /**
   * Custom serializer for MD5 hashing a string key, used for client mod verification. Generates a
   * random string if the original value is empty.
   */
  public static class MD5KeySerializer extends ConfigSerializer<byte[], String> {

    private final MessageDigest md5;
    private final Random random;
    private String originalValue;

    /**
     * Constructs the MD5KeySerializer.
     *
     * @throws NoSuchAlgorithmException if MD5 algorithm is not available.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public MD5KeySerializer() throws NoSuchAlgorithmException {
      super(byte[].class, String.class);
      this.md5 = MessageDigest.getInstance("MD5");
      this.random = new SecureRandom();
    }

    @Override
    public String serialize(byte[] from) {
      if (this.originalValue == null || this.originalValue.isEmpty()) {
        this.originalValue = generateRandomString(24);
      }
      return this.originalValue;
    }

    @Override
    public byte[] deserialize(String from) {
      this.originalValue = from;
      return this.md5.digest(from.getBytes(StandardCharsets.UTF_8));
    }

    private String generateRandomString(int length) {
      String chars = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz1234567890";
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < length; i++) {
        builder.append(chars.charAt(this.random.nextInt(chars.length())));
      }
      return builder.toString();
    }
  }
}
