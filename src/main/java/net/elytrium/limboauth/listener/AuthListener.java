package net.elytrium.limboauth.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.UUID;
import net.elytrium.commons.utils.reflection.ReflectionException;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.service.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Listener for authentication-related events during player login. Enforces offline/online mode
 * rules and handles login in Limbo.
 */
public class AuthListener {

  private static final MethodHandle DELEGATE_FIELD;

  static {
    try {
      DELEGATE_FIELD =
          MethodHandles.privateLookupIn(LoginInboundConnection.class, MethodHandles.lookup())
              .findGetter(LoginInboundConnection.class, "delegate", InitialInboundConnection.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ReflectionException(e);
    }
  }

  private final LimboAuth plugin;
  private final Logger logger;
  private final AuthenticationService authenticationService;
  private final PlayerSessionService playerSessionService;
  private final ConfigManager configManager;
  private final DatabaseService databaseService;
  private final CacheManager cacheManager;
  private final Component errorOccurredMessage;

  /**
   * Constructs the AuthListener.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param authenticationService Service for authentication logic.
   * @param playerSessionService Service for managing player sessions.
   * @param configManager Service for accessing configuration.
   * @param databaseService Service for database interactions.
   * @param cacheManager Service for managing caches.
   */
  public AuthListener(
      LimboAuth plugin,
      AuthenticationService authenticationService,
      PlayerSessionService playerSessionService,
      ConfigManager configManager,
      DatabaseService databaseService,
      CacheManager cacheManager) {
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
    this.authenticationService = authenticationService;
    this.playerSessionService = playerSessionService;
    this.configManager = configManager;
    this.databaseService = databaseService;
    this.cacheManager = cacheManager;
    this.errorOccurredMessage =
        this.configManager
            .getSerializer()
            .deserialize(configManager.getSettings().MAIN.STRINGS.ERROR_OCCURRED);
  }

  private MinecraftConnection getMinecraftConnection(InboundConnection inbound) {
    try {
      LoginInboundConnection inboundConnection = (LoginInboundConnection) inbound;
      InitialInboundConnection initialInbound =
          (InitialInboundConnection) DELEGATE_FIELD.invokeExact(inboundConnection);
      return initialInbound.getConnection();
    } catch (Throwable t) {
      this.logger.error(
          "Failed to get MinecraftConnection from InboundConnection for {}:",
          inbound.getRemoteAddress(),
          t);
      return null;
    }
  }

  /**
   * Handles PreLoginEvent. Determines if a connecting player should be forced into online or
   * offline mode based on premium status.
   *
   * @param event The PreLoginEvent.
   */
  @Subscribe(order = PostOrder.LATE)
  public void onPreLogin(com.velocitypowered.api.event.connection.PreLoginEvent event) {
    if (!event.getResult().isAllowed()) {
      return;
    }

    Settings currentSettings = this.configManager.getSettings();
    String username = event.getUsername();
    String usernameLower = username.toLowerCase(Locale.ROOT);

    try {
      if (cacheManager.isForcedOfflinePreviously(username)) {
        this.logger.debug(
            "Player {} was forced offline previously, respecting current offline connection.",
            username);
        return;
      }

      boolean determinedPremium = this.authenticationService.isPremium(username);

      if (determinedPremium) {
        event.setResult(
            com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult
                .forceOnlineMode());
        this.logger.debug("Player {} determined as premium, forcing online mode.", username);

        if (!currentSettings.MAIN.ONLINE_MODE_NEED_AUTH_STRICT) {
          LimboAuth.CachedPremiumUser premiumUserCacheEntry =
              this.cacheManager.getPremiumCacheEntry(usernameLower);
          boolean isTruePremiumInCache =
              premiumUserCacheEntry != null && premiumUserCacheEntry.isForcePremium();

          if (!isTruePremiumInCache
              && this.authenticationService.isPremiumInternal(usernameLower).getState()
                  == LimboAuth.PremiumState.UNKNOWN) {
            MinecraftConnection mc = getMinecraftConnection(event.getConnection());
            if (mc != null && !mc.isClosed()) {
              this.cacheManager.addPendingLogin(username);
              mc.getChannel()
                  .closeFuture()
                  .addListener(
                      future -> {
                        if (cacheManager.removePendingLogin(username)) {
                          this.logger.info(
                              "Player {} failed Mojang's online-mode check post-PreLogin; marking as non-premium.",
                              username);
                          this.cacheManager.setPremiumCacheEntry(usernameLower, false);
                        }
                      });
            }
          }
        }
      } else {
        this.logger.debug(
            "Player {} not premium, proceeding with default (likely offline) mode.", username);
      }
    } catch (Throwable throwable) {
      this.logger.error("Error during PreLoginEvent for {}: ", username, throwable);
      event.setResult(
          com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult.denied(
              this.errorOccurredMessage));
    }
  }

  /**
   * Schedules any queued post-login tasks for the player.
   *
   * @param event The PostLoginEvent.
   */
  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    Runnable postLoginTask =
        this.playerSessionService.getPostLoginTask(event.getPlayer().getUniqueId());
    if (postLoginTask != null) {
      this.plugin
          .getTaskSchedulingService()
          .scheduleDelayedTask(
              postLoginTask,
              this.configManager.getSettings().MAIN.PREMIUM_AND_FLOODGATE_MESSAGES_DELAY);
    }
  }

  /**
   * Handles the LoginLimboRegisterEvent after player passes auth. Updates cache and notifies Limbo
   * to allow the player through or initiate authentication.
   *
   * @param event The LoginLimboRegisterEvent from LimboAPI.
   */
  @Subscribe
  public void onLoginLimboRegister(LoginLimboRegisterEvent event) {
    Player player = event.getPlayer();
    String usernameLower = player.getUsername().toLowerCase(Locale.ROOT);
    GameProfile profile = player.getGameProfile();
    boolean isOnlineModePlayer = profile != null && !profile.getProperties().isEmpty();

    if (isOnlineModePlayer) {
      this.cacheManager.setForcedPremiumCacheLowercased(usernameLower, true);
      this.cacheManager.removePendingLogin(player.getUsername());
    }

    if (authenticationService.needsAuthentication(player)) {
      event.addOnJoinCallback(() -> this.authenticationService.handlePlayerAuthentication(player));
    } else {
      this.logger.debug(
          "Player {} session valid or auto-bypassing. Updating login data and passing Limbo.",
          player.getUsername());
      try {
        this.authenticationService.updateLoginData(player);
        this.plugin.getLimboFactory().passLoginLimbo(player);
      } catch (Exception e) {
        this.logger.error(
            "Error trying to directly pass player {} with valid session: {}",
            player.getUsername(),
            e.getMessage(),
            e);
        player.disconnect(errorOccurredMessage);
      }
    }
  }

  /**
   * Handles GameProfileRequestEvent. Updates the GameProfile with offline/online prefixes and saved
   * UUIDs according to plugin settings. This method also uses {@link
   * GameProfileRequestEvent#isOnlineMode()} to determine the connection's mode.
   *
   * @param event The GameProfileRequestEvent.
   */
  @Subscribe(order = PostOrder.FIRST)
  public void onGameProfileRequest(GameProfileRequestEvent event) {
    Settings currentSettings = this.configManager.getSettings();
    GameProfile originalProfile = event.getGameProfile();
    String username = originalProfile.getName();
    String usernameLower = username.toLowerCase(Locale.ROOT);
    GameProfile finalProfile = originalProfile;

    boolean isOnlineModeConnection = event.isOnlineMode(); // Velocity 3.4.0+ API

    if (currentSettings.MAIN.SAVE_UUID
        && (authenticationService.getFloodgateApiHolder() == null
            || !authenticationService.isFloodgatePlayer(originalProfile.getId()))) {

      RegisteredPlayer rpByAnyUUID = this.databaseService.findPlayerByUUID(originalProfile.getId());
      if (rpByAnyUUID != null
          && rpByAnyUUID.getUuid() != null
          && !rpByAnyUUID.getUuid().isEmpty()) {
        try {
          finalProfile = finalProfile.withId(UUID.fromString(rpByAnyUUID.getUuid()));
        } catch (IllegalArgumentException e) {
          this.logger.warn(
              "Invalid stored UUID for {} (found by current UUID {}): {}. Using original.",
              username,
              originalProfile.getId(),
              rpByAnyUUID.getUuid());
        }
      } else {
        RegisteredPlayer rpByName =
            this.databaseService.findPlayerByLowercaseNickname(usernameLower);
        if (rpByName != null) {
          boolean updated = false;
          if (rpByName.getUuid() == null || rpByName.getUuid().isEmpty()) {
            rpByName.setUuid(originalProfile.getId().toString());
            updated = true;
          }
          if (isOnlineModeConnection
              && (rpByName.getPremiumUuid() == null
                  || !rpByName.getPremiumUuid().equals(originalProfile.getId().toString()))) {
            rpByName.setPremiumUuid(originalProfile.getId().toString());
            updated = true;
          }
          if (updated) {
            this.databaseService.updatePlayer(rpByName);
          }
          if (rpByName.getUuid() != null && !rpByName.getUuid().isEmpty()) {
            try {
              finalProfile = finalProfile.withId(UUID.fromString(rpByName.getUuid()));
            } catch (IllegalArgumentException e) {
              this.logger.warn(
                  "Invalid stored UUID from name-match for {}: {}. Using original.",
                  username,
                  rpByName.getUuid());
            }
          }
        }
      }
    }

    if (isOnlineModeConnection) {
      RegisteredPlayer playerToUpdate =
          this.databaseService.findPlayerByLowercaseNickname(usernameLower);
      boolean changed = false;
      if (playerToUpdate != null) {
        if (!playerToUpdate.getHash().isEmpty()) {
          this.logger.info(
              "Player {} ({}) logged in with an online-mode account. Clearing password hash.",
              username,
              originalProfile.getId());
          playerToUpdate.setHash("");
          changed = true;
        }
        String currentPremiumUUID = originalProfile.getId().toString();
        if (playerToUpdate.getPremiumUuid() == null
            || !playerToUpdate.getPremiumUuid().equals(currentPremiumUUID)) {
          playerToUpdate.setPremiumUuid(currentPremiumUUID);
          changed = true;
        }
        if (currentSettings.MAIN.SAVE_UUID
            && (playerToUpdate.getUuid() == null
                || playerToUpdate.getUuid().isEmpty()
                || !playerToUpdate.getUuid().equals(currentPremiumUUID))) {
          playerToUpdate.setUuid(currentPremiumUUID);
          changed = true;
        }
        if (changed) {
          this.databaseService.updatePlayer(playerToUpdate);
        }
      }
    }

    if (currentSettings.MAIN.FORCE_OFFLINE_UUID) {
      finalProfile =
          finalProfile.withId(UuidUtils.generateOfflinePlayerUuid(finalProfile.getName()));
    }

    String nameForPrefixing = username;
    String newName = finalProfile.getName();

    if (!isOnlineModeConnection && !currentSettings.MAIN.OFFLINE_MODE_PREFIX.isEmpty()) {
      if (!nameForPrefixing.startsWith(currentSettings.MAIN.OFFLINE_MODE_PREFIX)) {
        newName = currentSettings.MAIN.OFFLINE_MODE_PREFIX + nameForPrefixing;
      }
    } else if (isOnlineModeConnection && !currentSettings.MAIN.ONLINE_MODE_PREFIX.isEmpty()) {
      if (!nameForPrefixing.startsWith(currentSettings.MAIN.ONLINE_MODE_PREFIX)) {
        newName = currentSettings.MAIN.ONLINE_MODE_PREFIX + nameForPrefixing;
      }
    }

    if (!newName.equals(finalProfile.getName())) {
      finalProfile = finalProfile.withName(newName);
    }

    if (!finalProfile.equals(originalProfile)) {
      event.setGameProfile(finalProfile);
    }
  }

  /**
   * Cleans up player session state on disconnect.
   *
   * @param event The DisconnectEvent.
   */
  @Subscribe
  public void onDisconnect(DisconnectEvent event) {
    try {
      Player player = event.getPlayer();
      String username = player.getUsername();
      this.playerSessionService.removeAuthenticatingPlayer(username);
      this.cacheManager.removePendingLogin(username);
      this.cacheManager.unsetForcedOfflinePreviously(username);
      this.logger.debug("Cleaned up session states for disconnected player: {}", username);
    } catch (Exception e) {
      this.logger.error(
          "Error during disconnect cleanup for {}", event.getPlayer().getUsername(), e);
    }
  }
}
