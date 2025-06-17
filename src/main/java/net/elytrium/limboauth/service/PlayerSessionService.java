// File: LimboAuth/src/main/java/net/elytrium/limboauth/service/PlayerSessionService.java
package net.elytrium.limboauth.service;

import com.google.inject.Inject;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.slf4j.Logger;

/**
 * Manages player authentication sessions, including tracking players currently in the
 * authentication process and their login attempts. It also handles post-login tasks.
 */
public class PlayerSessionService {

  private final Logger logger;
  private final Map<String, AuthSessionHandler> authenticatingPlayers = new ConcurrentHashMap<>();
  private final Map<UUID, Runnable> postLoginTasks = new ConcurrentHashMap<>();
  private final Map<String, Integer> playerLoginAttempts = new ConcurrentHashMap<>();

  private final DatabaseService databaseService;
  private final ConfigManager configManager;
  private final LimboAuth plugin;
  private AuthenticationService authenticationService;
  private final LimboServerFacade limboServerFacade;
  private final CacheManager cacheManager;

  /**
   * Constructs the PlayerSessionService. Dependencies are injected.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param logger The logger for this service.
   * @param databaseService Service for database interactions.
   * @param authenticationService Service for authentication logic (can be null initially).
   * @param limboServerFacade Facade for Limbo server interactions.
   * @param configManager Service for accessing configuration.
   * @param cacheManager Service for managing caches.
   */
  @Inject
  public PlayerSessionService(
      LimboAuth plugin,
      Logger logger,
      DatabaseService databaseService,
      AuthenticationService authenticationService,
      LimboServerFacade limboServerFacade,
      ConfigManager configManager,
      CacheManager cacheManager) {
    this.plugin = plugin;
    this.logger = logger;
    this.databaseService = databaseService;
    this.authenticationService = authenticationService;
    this.limboServerFacade = limboServerFacade;
    this.configManager = configManager;
    this.cacheManager = cacheManager;
  }

  /**
   * Initializes the PlayerSessionService. Currently logs an informational message.
   *
   * @param pluginInstance The main LimboAuth plugin instance.
   */
  public void initialize(LimboAuth pluginInstance) {
    this.logger.info("PlayerSessionService initialized.");
  }

  /**
   * Sets the {@link AuthenticationService} instance. This is used to resolve a potential circular
   * dependency during plugin initialization.
   *
   * @param authenticationService The AuthenticationService instance.
   */
  public void setAuthenticationService(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  /**
   * Reloads the PlayerSessionService. Currently logs an informational message.
   * Configuration-dependent settings should be re-applied here if any.
   *
   * @param newConfigManager The new ConfigManager instance after a reload.
   */
  public void reload(ConfigManager newConfigManager) {
    this.logger.info("PlayerSessionService reloaded.");
  }

  /**
   * Creates an {@link AuthSessionHandler} for a player entering the authentication process.
   *
   * @param proxyPlayer The Velocity {@link Player} instance.
   * @param playerInfo The {@link RegisteredPlayer} data.
   * @return A new {@link AuthSessionHandler} instance.
   */
  public AuthSessionHandler createAuthSessionHandler(
      Player proxyPlayer, RegisteredPlayer playerInfo) {
    // Corrected constructor call for AuthSessionHandler
    AuthSessionHandler handler = new AuthSessionHandler(this.plugin, proxyPlayer, this);
    addAuthenticatingPlayer(proxyPlayer.getUsername(), handler);
    return handler;
  }

  /**
   * Adds a player and their {@link AuthSessionHandler} to the map of authenticating players.
   * Initializes their login attempts based on configuration.
   *
   * @param nickname The player's nickname.
   * @param handler The player's {@link AuthSessionHandler}.
   */
  public void addAuthenticatingPlayer(String nickname, AuthSessionHandler handler) {
    authenticatingPlayers.put(nickname.toLowerCase(Locale.ROOT), handler);
    playerLoginAttempts.put(
        nickname.toLowerCase(Locale.ROOT), this.configManager.getSettings().MAIN.LOGIN_ATTEMPTS);
    this.logger.debug(
        "Player {} added to authenticating players map with {} attempts.",
        nickname,
        this.configManager.getSettings().MAIN.LOGIN_ATTEMPTS);
  }

  /**
   * Removes a player from the map of authenticating players and clears their login attempt count.
   *
   * @param nickname The player's nickname.
   */
  public void removeAuthenticatingPlayer(String nickname) {
    authenticatingPlayers.remove(nickname.toLowerCase(Locale.ROOT));
    playerLoginAttempts.remove(nickname.toLowerCase(Locale.ROOT));
    this.logger.debug("Player {} removed from authenticating players map.", nickname);
  }

  /**
   * Retrieves the {@link AuthSessionHandler} for a player currently in authentication.
   *
   * @param nickname The player's nickname.
   * @return The {@link AuthSessionHandler}, or {@code null} if the player is not authenticating.
   */
  public AuthSessionHandler getAuthenticatingPlayer(String nickname) {
    return authenticatingPlayers.get(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Gets a map of all players currently in the authentication process. The keys are lowercase
   * player nicknames, and values are their {@link AuthSessionHandler}s.
   *
   * @return A map of authenticating players. Consider returning an unmodifiable view if external
   *     modification is not desired.
   */
  public Map<String, AuthSessionHandler> getAuthenticatingPlayers() {
    return authenticatingPlayers;
  }

  /**
   * Adds a task to be executed after a player successfully logs in. The task is stored by player
   * UUID and will be removed after retrieval.
   *
   * @param playerId The UUID of the player.
   * @param task The {@link Runnable} task to execute.
   */
  public void addPostLoginTask(UUID playerId, Runnable task) {
    postLoginTasks.put(playerId, task);
    this.logger.debug("Post login task added for player UUID: {}", playerId);
  }

  /**
   * Retrieves and removes a post-login task for a player.
   *
   * @param playerId The UUID of the player.
   * @return The {@link Runnable} task, or {@code null} if no task was queued for this player.
   */
  public Runnable getPostLoginTask(UUID playerId) {
    return postLoginTasks.remove(playerId);
  }

  /**
   * Gets the number of remaining login attempts for a player.
   *
   * @param username The player's username.
   * @return The number of remaining attempts, or 0 if the player is not tracked or has no attempts
   *     left.
   */
  public int getRemainingLoginAttempts(String username) {
    return playerLoginAttempts.getOrDefault(username.toLowerCase(Locale.ROOT), 0);
  }

  /**
   * Decrements the number of login attempts for a player. The count will not go below zero.
   *
   * @param username The player's username.
   */
  public void decrementLoginAttempts(String username) {
    String lowerUser = username.toLowerCase(Locale.ROOT);
    playerLoginAttempts.computeIfPresent(lowerUser, (k, v) -> Math.max(0, v - 1));
    this.logger.debug(
        "Decremented login attempts for {}. Remaining: {}",
        username,
        getRemainingLoginAttempts(username));
  }

  /**
   * Resets the login attempts for a player to the configured maximum.
   *
   * @param username The player's username.
   */
  public void resetLoginAttempts(String username) {
    playerLoginAttempts.put(
        username.toLowerCase(Locale.ROOT), this.configManager.getSettings().MAIN.LOGIN_ATTEMPTS);
    this.logger.debug(
        "Reset login attempts for {} to {}.",
        username,
        this.configManager.getSettings().MAIN.LOGIN_ATTEMPTS);
  }

  /**
   * Checks if a player needs to register. This typically means they are not found in the database.
   *
   * @param username The player's username.
   * @return {@code true} if the player needs to register, {@code false} otherwise.
   */
  public boolean needsRegistration(String username) {
    return this.databaseService.findPlayerByLowercaseNickname(username.toLowerCase(Locale.ROOT))
        == null;
  }

  /**
   * Retrieves the RegisteredPlayer object for a given username.
   *
   * @param username The player's username.
   * @return The {@link RegisteredPlayer} object, or {@code null} if not found.
   */
  public RegisteredPlayer getRegisteredPlayer(String username) {
    return this.databaseService.findPlayerByLowercaseNickname(username.toLowerCase(Locale.ROOT));
  }
}
