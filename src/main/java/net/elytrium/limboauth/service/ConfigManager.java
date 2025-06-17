package net.elytrium.limboauth.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.commons.kyori.serialization.Serializers;
import net.elytrium.commons.utils.updates.UpdatesChecker;
import net.elytrium.limboauth.BuildConstants;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

/**
 * Manages the plugin's configuration (config.yml). Handles loading, reloading, and providing access
 * to settings, messages, and other configuration-dependent resources like unsafe password lists.
 */
public class ConfigManager {
  private final Logger logger;
  private Settings settings;
  private Serializer serializer;
  private Pattern nicknameValidationPattern;

  /** Set of unsafe passwords loaded from the configured file. */
  public Set<String> unsafePasswords = new HashSet<>();

  private Path dataDirectory;
  private Path configFile;

  /** Component for premium login message. */
  public Component loginPremium;

  /** Title for premium login. */
  public Title loginPremiumTitle;

  /** Component for Floodgate (Bedrock) login message. */
  public Component loginFloodgate;

  /** Title for Floodgate (Bedrock) login. */
  public Title loginFloodgateTitle;

  /** Component for kick message when registrations are disabled. */
  public Component registrationsDisabledKick;

  /** Component for kick message due to too many brute-force attempts. */
  public Component bruteforceAttemptKick;

  /** Component for kick message due to invalid nickname. */
  public Component nicknameInvalidKick;

  /** Component for kick message prompting reconnection. */
  public Component reconnectKick;

  /**
   * Constructs the ConfigManager.
   *
   * @param logger The logger for this service.
   */
  /** Default constructor. */
  public ConfigManager(Logger logger) {
    this.logger = logger;
    this.settings = Settings.IMP;
  }

  /**
   * Initializes the configuration by loading from the specified path. Sets up serializers,
   * validation patterns, unsafe passwords, and static messages.
   *
   * @param configPath Path to the config.yml file.
   * @throws RuntimeException if initialization fails.
   */
  public void initialize(Path configPath) {
    try {
      this.configFile = configPath;
      this.dataDirectory = configPath.getParent();
      Settings.IMP.reload(this.configFile.toFile(), Settings.IMP.PREFIX);
      this.settings = Settings.IMP;

      net.kyori.adventure.text.serializer.ComponentSerializer<Component, Component, String>
          kyoriSerializer = this.settings.SERIALIZER.getSerializer();
      if (kyoriSerializer == null) {
        this.logger.warn(
            "The specified serializer could not be found, using default (LEGACY_AMPERSAND).");
        this.serializer =
            new Serializer(Objects.requireNonNull(Serializers.LEGACY_AMPERSAND.getSerializer()));
      } else {
        this.serializer = new Serializer(kyoriSerializer);
      }

      this.nicknameValidationPattern = Pattern.compile(settings.MAIN.ALLOWED_NICKNAME_REGEX);
      loadUnsafePasswords();
      loadPublicMessagesAndTitles();

      TaskEvent.reload(this.serializer);
      AuthSessionHandler.reload(this.serializer, this.settings, this.unsafePasswords);

      this.logger.info("Configuration initialized successfully");
    } catch (Exception e) {
      this.logger.error("Failed to initialize configuration", e);
      throw new RuntimeException("Configuration initialization failed", e);
    }
  }

  /**
   * Reloads the configuration from disk. Re-initializes serializers, validation patterns, unsafe
   * passwords, and static messages.
   *
   * @throws RuntimeException if reloading fails.
   */
  public void reload() {
    try {
      if (this.configFile == null) {
        this.logger.error("Config file path not set, cannot reload.");
        return;
      }
      settings.reload(this.configFile.toFile(), settings.PREFIX);

      net.kyori.adventure.text.serializer.ComponentSerializer<Component, Component, String>
          kyoriSerializer = this.settings.SERIALIZER.getSerializer();
      if (kyoriSerializer == null) {
        this.logger.warn(
            "The specified serializer could not be found, using default (LEGACY_AMPERSAND).");
        this.serializer =
            new Serializer(Objects.requireNonNull(Serializers.LEGACY_AMPERSAND.getSerializer()));
      } else {
        this.serializer = new Serializer(kyoriSerializer);
      }

      this.nicknameValidationPattern = Pattern.compile(settings.MAIN.ALLOWED_NICKNAME_REGEX);
      loadUnsafePasswords();
      loadPublicMessagesAndTitles();

      TaskEvent.reload(this.serializer);
      AuthSessionHandler.reload(this.serializer, this.settings, this.unsafePasswords);

      this.logger.info("Configuration reloaded successfully");
    } catch (Exception e) {
      this.logger.error("Failed to reload configuration", e);
      throw new RuntimeException("Configuration reload failed", e);
    }
  }

  private void loadUnsafePasswords() {
    this.unsafePasswords.clear();
    if (settings.MAIN.CHECK_PASSWORD_STRENGTH) {
      Path unsafePasswordsFile = dataDirectory.resolve(settings.MAIN.UNSAFE_PASSWORDS_FILE);
      if (!Files.exists(unsafePasswordsFile)) {
        this.logger.info(
            "Unsafe passwords file not found, creating from resources: {}", unsafePasswordsFile);
        try (InputStream in =
            getClass().getResourceAsStream("/" + settings.MAIN.UNSAFE_PASSWORDS_FILE)) {
          if (in == null) {
            this.logger.error("Default unsafe_passwords.txt not found in plugin resources!");
            return;
          }
          Files.createDirectories(unsafePasswordsFile.getParent());
          Files.copy(in, unsafePasswordsFile);
        } catch (IOException e) {
          this.logger.error("Failed to copy default unsafe passwords file.", e);
          return;
        }
      }

      if (Files.exists(unsafePasswordsFile)) {
        try (Stream<String> unsafePasswordsStream = Files.lines(unsafePasswordsFile)) {
          this.unsafePasswords.addAll(unsafePasswordsStream.collect(Collectors.toList()));
          this.logger.info("Loaded {} unsafe passwords.", this.unsafePasswords.size());
        } catch (IOException e) {
          this.logger.error("Failed to load unsafe passwords file: {}", unsafePasswordsFile, e);
          this.unsafePasswords.clear();
        }
      }
    }
  }

  private void loadPublicMessagesAndTitles() {
    this.loginPremium =
        settings.MAIN.STRINGS.LOGIN_PREMIUM.isEmpty()
            ? null
            : serializer.deserialize(settings.MAIN.STRINGS.LOGIN_PREMIUM);
    if (settings.MAIN.STRINGS.LOGIN_PREMIUM_TITLE.isEmpty()
        && settings.MAIN.STRINGS.LOGIN_PREMIUM_SUBTITLE.isEmpty()) {
      this.loginPremiumTitle = null;
    } else {
      this.loginPremiumTitle =
          Title.title(
              serializer.deserialize(settings.MAIN.STRINGS.LOGIN_PREMIUM_TITLE),
              serializer.deserialize(settings.MAIN.STRINGS.LOGIN_PREMIUM_SUBTITLE),
              settings.MAIN.PREMIUM_TITLE_SETTINGS.toTimes());
    }

    this.loginFloodgate =
        settings.MAIN.STRINGS.LOGIN_FLOODGATE.isEmpty()
            ? null
            : serializer.deserialize(settings.MAIN.STRINGS.LOGIN_FLOODGATE);
    if (settings.MAIN.STRINGS.LOGIN_FLOODGATE_TITLE.isEmpty()
        && settings.MAIN.STRINGS.LOGIN_FLOODGATE_SUBTITLE.isEmpty()) {
      this.loginFloodgateTitle = null;
    } else {
      this.loginFloodgateTitle =
          Title.title(
              serializer.deserialize(settings.MAIN.STRINGS.LOGIN_FLOODGATE_TITLE),
              serializer.deserialize(settings.MAIN.STRINGS.LOGIN_FLOODGATE_SUBTITLE),
              settings.MAIN.PREMIUM_TITLE_SETTINGS.toTimes());
    }
    this.bruteforceAttemptKick =
        serializer.deserialize(settings.MAIN.STRINGS.LOGIN_WRONG_PASSWORD_KICK);
    this.nicknameInvalidKick = serializer.deserialize(settings.MAIN.STRINGS.NICKNAME_INVALID_KICK);
    this.reconnectKick = serializer.deserialize(settings.MAIN.STRINGS.RECONNECT_KICK);
    this.registrationsDisabledKick =
        serializer.deserialize(settings.MAIN.STRINGS.REGISTRATIONS_DISABLED_KICK);
  }

  /**
   * @return The current {@link Settings} instance.
   */
  public Settings getSettings() {
    return settings;
  }

  /**
   * @return The current {@link Serializer} instance for messages.
   */
  public Serializer getSerializer() {
    return this.serializer;
  }

  /**
   * @return The compiled {@link Pattern} for nickname validation.
   */
  public Pattern getNicknameValidationPattern() {
    return nicknameValidationPattern;
  }

  /**
   * @return The plugin's data directory path.
   */
  public Path getDataDirectory() {
    return dataDirectory;
  }

  /**
   * @return The configured {@link Settings.MAIN.COMMAND_PERMISSION_STATE} for commands.
   */
  public Settings.MAIN.COMMAND_PERMISSION_STATE getCommandPermissionState() {
    return settings.MAIN.COMMAND_PERMISSION_STATE;
  }

  /**
   * @return True if the backend API is enabled, false otherwise.
   */
  public boolean isBackendApiEnabled() {
    return settings.MAIN.BACKEND_API.ENABLED;
  }

  /**
   * Adds custom metrics charts to bStats.
   *
   * @param metrics The bStats {@link Metrics} instance.
   * @param databaseService The {@link DatabaseService} for fetching player counts.
   */
  public void addMetricsCharts(Metrics metrics, DatabaseService databaseService) {
    metrics.addCustomChart(
        new SingleLineChart(
            "registered_players",
            () -> {
              try {
                // Corrected: Use the 'databaseService' parameter
                return (int) this.databaseService.getRegisteredPlayerCount();
              } catch (Exception e) { // NOSONAR - bStats should not crash the plugin
                this.logger.error("Failed to get registered player count for bStats", e);
                return 0;
              }
            }));

    metrics.addCustomChart(
        new SimplePie("database_type", () -> settings.DATABASE.STORAGE_TYPE.name()));

    metrics.addCustomChart(
        new SimplePie(
            "online_mode_need_auth", () -> String.valueOf(settings.MAIN.ONLINE_MODE_NEED_AUTH)));

    this.logger.debug("Added metrics charts successfully");
  }

  /**
   * Checks for plugin updates against the version specified at a remote URL.
   *
   * @return True if the current version is up-to-date or newer, false if an update is available.
   */
  public boolean checkUpdates() {
    this.logger.debug("Checking for updates...");
    return UpdatesChecker.checkVersionByURL(
        "https://raw.githubusercontent.com/Elytrium/LimboAuth/master/VERSION",
        BuildConstants.AUTH_VERSION);
  }

  /**
   * @return Milliseconds for general cache purging.
   */
  public long getPurgeCacheMillis() {
    return settings.MAIN.PURGE_CACHE_MILLIS;
  }

  /**
   * @return Milliseconds for premium cache purging.
   */
  public long getPurgePremiumCacheMillis() {
    return settings.MAIN.PURGE_PREMIUM_CACHE_MILLIS;
  }

  /**
   * @return Milliseconds for bruteforce cache purging.
   */
  public long getPurgeBruteforceCacheMillis() {
    return settings.MAIN.PURGE_BRUTEFORCE_CACHE_MILLIS;
  }

  /**
   * @return True if Floodgate players need authentication.
   */
  public boolean isFloodgateNeedAuth() {
    return settings.MAIN.FLOODGATE_NEED_AUTH;
  }

  /**
   * @return Maximum bruteforce attempts allowed.
   */
  public int getBruteforceMaxAttempts() {
    return settings.MAIN.BRUTEFORCE_MAX_ATTEMPTS;
  }

  /**
   * @return True if premium accounts should be saved in the database.
   */
  public boolean savePremiumAccounts() {
    return settings.MAIN.SAVE_PREMIUM_ACCOUNTS;
  }

  /**
   * @return True if online-mode players need authentication.
   */
  public boolean isOnlineModeNeedAuth() {
    return settings.MAIN.ONLINE_MODE_NEED_AUTH;
  }

  /**
   * @return True if new registrations are disabled.
   */
  public boolean isRegistrationsDisabled() {
    return settings.MAIN.DISABLE_REGISTRATIONS;
  }

  /**
   * @return True if client mod integration is enabled.
   */
  public boolean isModEnabled() {
    return settings.MAIN.MOD.ENABLED;
  }

  /**
   * @return The verification key for client mod communication.
   */
  public byte[] getModVerifyKey() {
    return settings.MAIN.MOD.VERIFY_KEY;
  }

  /**
   * @return The URL for external premium authentication checks.
   */
  public String getIsPremiumAuthUrl() {
    return settings.MAIN.ISPREMIUM_AUTH_URL;
  }

  /**
   * @return List of HTTP status codes indicating a rate limit from the premium check API.
   */
  public List<Integer> getStatusCodeRateLimit() {
    return settings.MAIN.STATUS_CODE_RATE_LIMIT;
  }

  /**
   * @return List of HTTP status codes indicating a user does not exist from the premium check API.
   */
  public List<Integer> getStatusCodeUserNotExists() {
    return settings.MAIN.STATUS_CODE_USER_NOT_EXISTS;
  }

  /**
   * @return List of HTTP status codes indicating a user exists from the premium check API.
   */
  public List<Integer> getStatusCodeUserExists() {
    return settings.MAIN.STATUS_CODE_USER_EXISTS;
  }

  /**
   * @return List of JSON fields to validate in the response when a user exists.
   */
  public List<String> getUserExistsJsonValidatorFields() {
    return settings.MAIN.USER_EXISTS_JSON_VALIDATOR_FIELDS;
  }

  /**
   * @return The JSON field name containing the UUID in the premium check response.
   */
  public String getJsonUuidField() {
    return settings.MAIN.JSON_UUID_FIELD;
  }

  /**
   * @return List of JSON fields to validate in the response when a user does not exist.
   */
  public List<String> getUserNotExistsJsonValidatorFields() {
    return settings.MAIN.USER_NOT_EXISTS_JSON_VALIDATOR_FIELDS;
  }

  /**
   * @return True if players should be treated as premium on API rate limit, false for cracked.
   */
  public boolean onRateLimitPremium() {
    return settings.MAIN.ON_RATE_LIMIT_PREMIUM;
  }

  /**
   * @return True if players should be treated as premium on API server error, false for cracked.
   */
  public boolean onServerErrorPremium() {
    return settings.MAIN.ON_SERVER_ERROR_PREMIUM;
  }

  /**
   * @return List of enabled backend API endpoint names.
   */
  public List<String> getEnabledBackendEndpoints() {
    return settings.MAIN.BACKEND_API.ENABLED_ENDPOINTS;
  }
}
