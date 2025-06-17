package net.elytrium.limboauth.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.util.GameProfile;
import io.whitfin.siphash.SipHasher;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.event.PreAuthorizationEvent;
import net.elytrium.limboauth.event.PreEvent;
import net.elytrium.limboauth.event.PreRegisterEvent;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.floodgate.FloodgateApiHolder;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.migration.MigrationHash;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import org.slf4j.Logger;

/**
 * Service responsible for handling player authentication logic, including premium checks, password
 * verification, and interaction with Floodgate.
 */
public class AuthenticationService {

  private final Logger logger;
  private final DatabaseService databaseService;
  private final CacheManager cacheManager;
  private final ConfigManager configManager;
  private LimboServerFacade limboServerFacade;
  private PlayerSessionService playerSessionService;
  private final ProxyServer proxyServer;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private FloodgateApiHolder floodgateApi;

  private static final ChannelIdentifier MOD_CHANNEL =
      MinecraftChannelIdentifier.create("limboauth", "mod/541f59e4256a337ea252bc482a009d46");
  private static final ChannelIdentifier LEGACY_MOD_CHANNEL =
      new LegacyChannelIdentifier("LIMBOAUTH|MOD");
  private static final BCrypt.Verifyer HASH_VERIFIER = BCrypt.verifyer();

  /**
   * Constructs the AuthenticationService.
   *
   * @param proxyServer The ProxyServer instance.
   * @param logger The logger for this service.
   * @param databaseService Service for database interactions.
   * @param cacheManager Service for managing caches.
   * @param configManager Service for accessing configuration.
   * @param limboServerFacade Facade for interacting with the Limbo this.server.
   * @param playerSessionService Service for managing player sessions.
   */
  public AuthenticationService(
      ProxyServer proxyServer,
      Logger logger,
      DatabaseService databaseService,
      CacheManager cacheManager,
      ConfigManager configManager,
      LimboServerFacade limboServerFacade,
      PlayerSessionService playerSessionService) {
    this.proxyServer = proxyServer;
    this.logger = logger;
    this.databaseService = databaseService;
    this.cacheManager = cacheManager;
    this.configManager = configManager;
    this.limboServerFacade = limboServerFacade;
    this.playerSessionService = playerSessionService;
  }

  /**
   * Sets the LimboServerFacade. Used for dependency injection.
   *
   * @param limboServerFacade The LimboServerFacade instance.
   */
  public void setLimboServerFacade(LimboServerFacade limboServerFacade) {
    this.limboServerFacade = limboServerFacade;
  }

  /**
   * Sets the PlayerSessionService. Used for dependency injection.
   *
   * @param playerSessionService The PlayerSessionService instance.
   */
  public void setPlayerSessionService(PlayerSessionService playerSessionService) {
    this.playerSessionService = playerSessionService;
  }

  /**
   * Gets the Floodgate API holder.
   *
   * @return The FloodgateApiHolder, or null if Floodgate is not present/integrated.
   */
  public FloodgateApiHolder getFloodgateApiHolder() {
    return this.floodgateApi;
  }

  /**
   * Sets the Floodgate API holder.
   *
   * @param floodgateApi The FloodgateApiHolder instance.
   */
  public void setFloodgateApi(FloodgateApiHolder floodgateApi) {
    this.floodgateApi = floodgateApi;
    if (this.floodgateApi == null && !configManager.isFloodgateNeedAuth()) {
      this.logger.warn(
          "Floodgate integration is configured for auto-login, but FloodgateAPI is not available. Bedrock players might not auto-login.");
    }
  }

  /**
   * Checks if a player is a Floodgate (Bedrock) player.
   *
   * @param uuid The UUID of the player.
   * @return True if the player is a Floodgate player, false otherwise.
   */
  public boolean isFloodgatePlayer(UUID uuid) {
    return floodgateApi != null && floodgateApi.isFloodgatePlayer(uuid);
  }

  /**
   * Gets the length of the Floodgate player prefix.
   *
   * @return The length of the prefix, or 0 if Floodgate is not available.
   */
  public int getFloodgatePrefixLength() {
    return floodgateApi != null ? floodgateApi.getPrefixLength() : 0;
  }

  /**
   * Handles the main authentication flow for a connecting player. This includes checks for Bedrock
   * auto-login, brute-force attempts, nickname validation, and dispatching to either registration
   * or login procedures.
   *
   * @param player The player to authenticate.
   */
  public void handlePlayerAuthentication(Player player) {
    boolean isBedrockPlayer = isFloodgatePlayer(player.getUniqueId());
    boolean autoLoginBedrock = !configManager.isFloodgateNeedAuth() && isBedrockPlayer;

    if (cacheManager.isForcedOfflinePreviously(player.getUsername())
        && isPremium(player.getUsername())) {
      if (!autoLoginBedrock) {
        player.disconnect(configManager.reconnectKick);
        return;
      }
    }

    if (cacheManager.getBruteforceAttempts(player.getRemoteAddress().getAddress())
        >= this.configManager.getBruteforceMaxAttempts()) {
      player.disconnect(configManager.bruteforceAttemptKick);
      return;
    }

    String nickname = player.getUsername();
    String validationName = nickname;
    if (isBedrockPlayer
        && floodgateApi != null
        && nickname.startsWith(floodgateApi.getPlayerPrefix())) {
      validationName = nickname.substring(floodgateApi.getPrefixLength());
    }

    if (!configManager.getNicknameValidationPattern().matcher(validationName).matches()) {
      player.disconnect(configManager.nicknameInvalidKick);
      return;
    }

    RegisteredPlayer registeredPlayer =
        this.databaseService.findPlayerByLowercaseNickname(nickname.toLowerCase(Locale.ROOT));
    GameProfile gameProfile = player.getGameProfile();
    boolean onlineMode = gameProfile != null && !gameProfile.getProperties().isEmpty();

    TaskEvent.Result result = TaskEvent.Result.NORMAL;

    if (onlineMode || autoLoginBedrock) {
      if (registeredPlayer == null || registeredPlayer.getHash().isEmpty()) {
        RegisteredPlayer byUuid =
            this.databaseService.findPlayerByPremiumUUID(player.getUniqueId());

        if (registeredPlayer != null && byUuid == null && registeredPlayer.getHash().isEmpty()) {
          registeredPlayer.setPremiumUuid(player.getUniqueId());
          this.databaseService.updatePlayer(registeredPlayer);
          byUuid = registeredPlayer;
        }
        registeredPlayer = byUuid;

        if (registeredPlayer == null && this.configManager.savePremiumAccounts()) {
          registeredPlayer = new RegisteredPlayer(player).setPremiumUuid(player.getUniqueId());
          registeredPlayer.setHash("");
          this.databaseService.createPlayer(registeredPlayer);
        }

        if (registeredPlayer == null
            || (!registeredPlayer.getHash().isEmpty() && !configManager.isOnlineModeNeedAuth())) {
          final Player finalPlayer = player;
          final boolean finalOnlineMode = onlineMode;

          this.playerSessionService.addPostLoginTask(
              player.getUniqueId(),
              () -> {
                if (finalOnlineMode) {
                  if (configManager.loginPremium != null)
                    finalPlayer.sendMessage(configManager.loginPremium);
                  if (configManager.loginPremiumTitle != null)
                    finalPlayer.showTitle(configManager.loginPremiumTitle);
                } else {
                  if (configManager.loginFloodgate != null)
                    finalPlayer.sendMessage(configManager.loginFloodgate);
                  if (configManager.loginFloodgateTitle != null)
                    finalPlayer.showTitle(configManager.loginFloodgateTitle);
                }
              });
          result = TaskEvent.Result.BYPASS;
        }
      }
    }

    EventManager eventManager = proxyServer.getEventManager();
    if (result != TaskEvent.Result.BYPASS) {
      if (registeredPlayer == null) {
        if (configManager.isRegistrationsDisabled()) {
          player.disconnect(configManager.registrationsDisabledKick);
          return;
        }
        Consumer<TaskEvent> eventConsumer = (event) -> processPlayerDispatch(event, null);
        eventManager
            .fire(new PreRegisterEvent(eventConsumer, result, player))
            .thenAcceptAsync(eventConsumer);
      } else {
        Consumer<TaskEvent> eventConsumer =
            (event) ->
                processPlayerDispatch(event, ((PreAuthorizationEvent) event).getPlayerInfo());
        eventManager
            .fire(new PreAuthorizationEvent(eventConsumer, result, player, registeredPlayer))
            .thenAcceptAsync(eventConsumer);
      }
    } else {
      try {
        this.cacheManager.cacheAuthUser(player);
        updateLoginData(player);
      } finally {
        this.limboServerFacade.passLoginLimbo(player);
      }
    }
  }

  private void processPlayerDispatch(TaskEvent event, RegisteredPlayer registeredPlayer) {
    Player player = ((PreEvent) event).getPlayer();
    switch (event.getResult()) {
      case BYPASS:
        try {
          this.cacheManager.cacheAuthUser(player);
          updateLoginData(player);
        } finally {
          this.limboServerFacade.passLoginLimbo(player);
        }
        break;
      case CANCEL:
        player.disconnect(event.getReason());
        break;
      case WAIT:
        // Waiting for async event handlers, do nothing here.
        break;
      case NORMAL:
      default:
        AuthSessionHandler handler =
            this.playerSessionService.createAuthSessionHandler(player, registeredPlayer);
        this.limboServerFacade.spawnPlayerInLimbo(player, handler);
        break;
    }
  }

  /**
   * Updates login data for a player (IP, login date) and sends mod integration messages if enabled.
   *
   * @param player The player whose data is to be updated.
   */
  public void updateLoginData(Player player) {
    String lowercaseNickname = player.getUsername().toLowerCase(Locale.ROOT);
    RegisteredPlayer rp = this.databaseService.findPlayerByLowercaseNickname(lowercaseNickname);
    if (rp != null) {
      rp.setLoginIp(player.getRemoteAddress().getAddress().getHostAddress());
      rp.setLoginDate(System.currentTimeMillis());
      this.databaseService.updatePlayer(rp);

      if (configManager.isModEnabled()) {
        byte[] lowercaseNicknameSerialized = lowercaseNickname.getBytes(StandardCharsets.UTF_8);
        long issueTime = System.currentTimeMillis();
        long hash =
            SipHasher.init(configManager.getModVerifyKey())
                .update(lowercaseNicknameSerialized)
                .update(Longs.toByteArray(issueTime))
                .digest();
        player.sendPluginMessage(
            getChannelIdentifier(player),
            Bytes.concat(Longs.toByteArray(issueTime), Longs.toByteArray(hash)));
      }
    } else {
      this.logger.debug(
          "Attempted to update login data for player not found in DB (possibly a new premium player with save-premium-accounts: false): {}",
          lowercaseNickname);
    }
  }

  /**
   * Gets the appropriate plugin message channel identifier based on the player's protocol version.
   *
   * @param player The player.
   * @return The channel identifier for mod communication.
   */
  public ChannelIdentifier getChannelIdentifier(Player player) {
    return player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_13) >= 0
        ? MOD_CHANNEL
        : LEGACY_MOD_CHANNEL;
  }

  private boolean validateScheme(JsonElement jsonElement, List<String> scheme) {
    if (!scheme.isEmpty()) {
      if (!(jsonElement instanceof JsonObject)) {
        return false;
      }
      JsonObject object = (JsonObject) jsonElement;
      for (String field : scheme) {
        if (!object.has(field)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks if a username is premium by querying an external API (e.g., Mojang). Handles different
   * HTTP status codes and JSON response validation as configured.
   *
   * @param nickname The username to check.
   * @return A {@link LimboAuth.PremiumResponse} indicating the premium status and UUID if
   *     available.
   */
  public LimboAuth.PremiumResponse isPremiumExternal(String nickname) {
    try {
      HttpResponse<String> response =
          this.httpClient.send(
              HttpRequest.newBuilder()
                  .uri(
                      URI.create(
                          String.format(
                              this.configManager.getIsPremiumAuthUrl(),
                              URLEncoder.encode(nickname, StandardCharsets.UTF_8))))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      int statusCode = response.statusCode();

      if (configManager.getStatusCodeRateLimit().contains(statusCode)) {
        return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.RATE_LIMIT);
      }

      String responseBody = response.body();
      if (responseBody == null || responseBody.trim().isEmpty()) {
        if (configManager.getStatusCodeUserNotExists().contains(statusCode)) {
          return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.CRACKED);
        }
        this.logger.warn(
            "Premium check for {} resulted in status code {} with empty body.",
            nickname,
            statusCode);
        return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.ERROR);
      }

      JsonElement jsonElement = JsonParser.parseString(responseBody);

      if (configManager.getStatusCodeUserExists().contains(statusCode)
          && this.validateScheme(
              jsonElement, this.configManager.getUserExistsJsonValidatorFields())) {
        return new LimboAuth.PremiumResponse(
            LimboAuth.PremiumState.PREMIUM_USERNAME,
            ((JsonObject) jsonElement).get(configManager.getJsonUuidField()).getAsString());
      }

      if (configManager.getStatusCodeUserNotExists().contains(statusCode)
          && this.validateScheme(
              jsonElement, this.configManager.getUserNotExistsJsonValidatorFields())) {
        return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.CRACKED);
      }
      this.logger.warn(
          "Premium check for {} resulted in an unexpected status code {} or JSON scheme. Body: {}",
          nickname,
          statusCode,
          responseBody);
      return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.ERROR);
    } catch (Throwable t) {
      this.logger.error("Unable to authenticate with Mojang for user {}.", nickname, t);
      return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.ERROR);
    }
  }

  /**
   * Checks if a username is considered premium based on internal database records. A player is
   * premium internally if they are registered and have no password hash.
   *
   * @param nickname The username to check.
   * @return A {@link LimboAuth.PremiumResponse} indicating the premium status and stored premium
   *     UUID if applicable.
   */
  public LimboAuth.PremiumResponse isPremiumInternal(String nickname) {
    RegisteredPlayer player =
        this.databaseService.findPlayerByLowercaseNickname(nickname.toLowerCase(Locale.ROOT));
    if (player != null) {
      if (player.getHash().isEmpty()) {
        return new LimboAuth.PremiumResponse(
            LimboAuth.PremiumState.PREMIUM, player.getPremiumUuid());
      } else {
        return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.CRACKED);
      }
    }
    return new LimboAuth.PremiumResponse(LimboAuth.PremiumState.UNKNOWN);
  }

  /**
   * Checks if a given UUID is associated with an internally marked premium account.
   *
   * @param uuid The premium UUID to check.
   * @return True if a player with this premium UUID exists and has no password hash, false
   *     otherwise.
   */
  public boolean isPremiumUuid(UUID uuid) {
    RegisteredPlayer player = this.databaseService.findPlayerByPremiumUUID(uuid);
    return player != null && player.getHash().isEmpty();
  }

  @SafeVarargs
  private boolean checkIsPremiumAndCache(
      String nickname, Function<String, LimboAuth.PremiumResponse>... functions) {
    String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    LimboAuth.CachedPremiumUser cachedUser =
        this.cacheManager.getPremiumCacheEntry(lowercaseNickname);
    if (cachedUser != null) {
      return cachedUser.isPremium();
    }

    boolean premium = false;
    boolean unknown = false;
    boolean wasRateLimited = false;
    boolean wasError = false;
    UUID uuidFromExternal = null;

    for (Function<String, LimboAuth.PremiumResponse> function : functions) {
      LimboAuth.PremiumResponse check;
      try {
        check = function.apply(lowercaseNickname);
      } catch (Throwable t) {
        check = new LimboAuth.PremiumResponse(LimboAuth.PremiumState.ERROR);
        this.logger.error(
            "Unable to check player {} account state using one of the methods.",
            lowercaseNickname,
            t);
      }

      if (check.getUuid() != null) {
        uuidFromExternal = check.getUuid();
      }

      switch (check.getState()) {
        case CRACKED:
          this.cacheManager.setPremiumCacheEntry(lowercaseNickname, false);
          return false;
        case PREMIUM:
          this.cacheManager.setForcedPremiumCacheLowercased(lowercaseNickname, true);
          return true;
        case PREMIUM_USERNAME:
          premium = true;
          break;
        case UNKNOWN:
          unknown = true;
          break;
        case RATE_LIMIT:
          wasRateLimited = true;
          break;
        default:
        case ERROR:
          wasError = true;
          break;
      }
    }

    if (premium) {
      this.cacheManager.setPremiumCacheEntry(lowercaseNickname, true);
      return true;
    }

    if (unknown) {
      if (uuidFromExternal != null && isPremiumUuid(uuidFromExternal)) {
        this.cacheManager.setForcedPremiumCacheLowercased(lowercaseNickname, true);
        return true;
      }
      if (!configManager.isOnlineModeNeedAuth()) {
        this.cacheManager.setPremiumCacheEntry(lowercaseNickname, false);
        return false;
      }
    }

    if (wasRateLimited && !premium && !unknown) {
      boolean decision = this.configManager.onRateLimitPremium();
      this.cacheManager.setPremiumCacheEntry(lowercaseNickname, decision);
      return decision;
    }

    if (wasError && !premium && !unknown && !wasRateLimited) {
      boolean decision = this.configManager.onServerErrorPremium();
      this.cacheManager.setPremiumCacheEntry(lowercaseNickname, decision);
      return decision;
    }

    this.cacheManager.setPremiumCacheEntry(lowercaseNickname, false);
    return false;
  }

  /**
   * Determines if a player is premium, considering cache, internal checks, external checks, and
   * configuration for forced offline mode or fallback behaviors on API errors/rate limits.
   *
   * @param nickname The username to check.
   * @return True if the player is considered premium, false otherwise.
   */
  public boolean isPremium(String nickname) {
    if (configManager.getSettings().MAIN.FORCE_OFFLINE_MODE) {
      return false;
    }
    String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    LimboAuth.CachedPremiumUser cached = this.cacheManager.getPremiumCacheEntry(lowercaseNickname);
    if (cached != null) {
      return cached.isPremium();
    }

    boolean result;
    if (configManager.getSettings().MAIN.CHECK_PREMIUM_PRIORITY_INTERNAL) {
      result = checkIsPremiumAndCache(nickname, this::isPremiumInternal, this::isPremiumExternal);
    } else {
      result = checkIsPremiumAndCache(nickname, this::isPremiumExternal, this::isPremiumInternal);
    }
    return result;
  }

  /**
   * Checks a given password against a player's stored hash. Handles BCrypt verification and
   * potential hash migration from older algorithms.
   *
   * @param password The plaintext password to check.
   * @param player The {@link RegisteredPlayer} whose hash is to be checked against.
   * @return True if the password matches the stored hash (or a migrated hash), false otherwise.
   * @throws SQLRuntimeException if updating the hash after migration fails.
   */
  public boolean checkPassword(String password, RegisteredPlayer player) {
    String hash = player.getHash();
    if (hash == null || hash.isEmpty()) {
      this.logger.warn(
          "Attempted to check password for player {} with empty hash.",
          player.getLowercaseNickname());
      return false;
    }

    boolean isCorrect =
        HASH_VERIFIER.verify(
                password.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))
            .verified;

    if (!isCorrect && this.configManager.getSettings().MAIN.MIGRATION_HASH != null) {
      MigrationHash migrationHashType = this.configManager.getSettings().MAIN.MIGRATION_HASH;
      isCorrect = migrationHashType.checkPassword(hash, password);
      if (isCorrect) {
        this.logger.info(
            "Password for user {} successfully migrated from {} to BCrypt.",
            player.getLowercaseNickname(),
            migrationHashType);
        player.setPassword(password);
        try {
          this.databaseService.updatePlayer(player);
        } catch (SQLRuntimeException e) {
          this.logger.error(
              "Failed to update password hash after migration for user {}.",
              player.getLowercaseNickname(),
              e);
          throw e; // Re-throw to indicate failure to the caller
        }
      }
    }
    return isCorrect;
  }

  /**
   * Checks if a player needs authentication based on cache status (e.g., new session, IP change).
   *
   * @param player The player to check.
   * @return True if authentication is required, false otherwise.
   */
  public boolean needsAuthentication(Player player) {
    return this.cacheManager.needsAuth(player);
  }

  /**
   * Initiates the authentication process for a player by creating a session handler and spawning
   * them into the Limbo world.
   *
   * @param player The player to start authentication for.
   */
  public void startAuthenticationProcess(Player player) {
    RegisteredPlayer rp =
        this.databaseService.findPlayerByLowercaseNickname(
            player.getUsername().toLowerCase(Locale.ROOT));
    AuthSessionHandler handler = this.playerSessionService.createAuthSessionHandler(player, rp);
    this.limboServerFacade.spawnPlayerInLimbo(player, handler);
  }
}
