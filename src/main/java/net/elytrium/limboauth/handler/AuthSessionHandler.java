// File: LimboAuth/src/main/java/net/elytrium/limboauth/handler/AuthSessionHandler.java
package net.elytrium.limboauth.handler;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.text.MessageFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.PostAuthorizationEvent;
import net.elytrium.limboauth.event.PreAuthorizationEvent;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.service.PlayerSessionService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

/** Handles authentication sessions for players in the Limbo world. */
public class AuthSessionHandler implements LimboSessionHandler {

  /**
   * Code verifier for TOTP (Time-based One-Time Password) authentication. Uses default code
   * generator and system time provider.
   */
  /**
   * TOTP code verifier instance for two-factor authentication. Uses default code generator with
   * system time provider for validating time-based one-time passwords.
   */
  public static final CodeVerifier TOTP_CODE_VERIFIER =
      new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

  private static Serializer serializer;
  private static Settings.MAIN settingsFramework;
  private static Set<String> unsafePasswordsFramework;

  private final LimboAuth plugin;
  private final Logger logger;
  private final Player player; // Velocity player
  private final PlayerSessionService playerSessionService;

  private LimboPlayer limboPlayer; // LimboAPI player
  private boolean passwordAuthenticated = false;
  private int loginAttempts = 0;
  private ScheduledTask timeoutTask;
  private BossBar activeBossBar;
  private ScheduledTask bossBarUpdateScheduleTask;

  /**
   * Constructs an AuthSessionHandler for a player.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param player The player being authenticated.
   * @param playerSessionService The service managing player sessions.
   */
  /**
   * Constructs a new AuthSessionHandler for the specified player.
   *
   * @param plugin The main LimboAuth plugin instance
   * @param player The player who needs to be authenticated
   * @param playerSessionService The service managing player sessions
   */
  public AuthSessionHandler(
      LimboAuth plugin, Player player, PlayerSessionService playerSessionService) {
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
    this.player = player;
    this.playerSessionService = playerSessionService;
  }

  /**
   * Reloads static configuration for AuthSessionHandler.
   *
   * @param newSerializer The new message serializer
   * @param newSettings The updated plugin settings
   * @param newUnsafePasswords Set of unsafe passwords
   */
  public static void reload(
      Serializer newSerializer, Settings newSettings, Set<String> newUnsafePasswords) {
    AuthSessionHandler.serializer = newSerializer;
    AuthSessionHandler.settingsFramework = newSettings.MAIN;
    AuthSessionHandler.unsafePasswordsFramework = newUnsafePasswords;
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer limboPlayer) {
    this.limboPlayer = limboPlayer;
    this.loginAttempts = 0;

    this.logger.debug(
        "Player {} spawned in authentication Limbo (AuthSessionHandler.onSpawn)",
        player.getUsername());

    setupAuthenticationTimeout();
    setupBossBar();

    if (playerSessionService.needsRegistration(player.getUsername())) {
      sendRegistrationPrompt();
    } else {
      sendLoginPrompt();
    }
  }

  private void setupAuthenticationTimeout() {
    if (timeoutTask != null
        && timeoutTask.status() != TaskStatus.CANCELLED
        && timeoutTask.status() != TaskStatus.FINISHED) {
      timeoutTask.cancel();
    }
    timeoutTask =
        this.plugin
            .getTaskSchedulingService()
            .scheduleOnce(
                () -> {
                  if (player.isActive() && limboPlayer != null) {
                    Component timeoutMessage =
                        serializer.deserialize(settingsFramework.STRINGS.TIMES_UP);
                    limboPlayer.disconnect(); // Disconnect LimboPlayer first
                    player.disconnect(timeoutMessage); // Then Velocity Player
                    this.playerSessionService.removeAuthenticatingPlayer(player.getUsername());
                  }
                },
                settingsFramework.AUTH_TIME,
                TimeUnit.MILLISECONDS);
  }

  private void setupBossBar() {
    if (!settingsFramework.ENABLE_BOSSBAR) {
      return;
    }
    if (activeBossBar != null) {
      player.hideBossBar(activeBossBar);
    }
    if (bossBarUpdateScheduleTask != null
        && bossBarUpdateScheduleTask.status() != TaskStatus.CANCELLED
        && bossBarUpdateScheduleTask.status() != TaskStatus.FINISHED) {
      bossBarUpdateScheduleTask.cancel();
    }

    activeBossBar =
        BossBar.bossBar(
            Component.empty(),
            1.0f,
            settingsFramework.BOSSBAR_COLOR,
            settingsFramework.BOSSBAR_OVERLAY);
    player.showBossBar(activeBossBar);
    startBossBarUpdates();
  }

  private void startBossBarUpdates() {
    final long totalAuthTimeSeconds = settingsFramework.AUTH_TIME / 1000;
    final long[] secondsRemaining = {totalAuthTimeSeconds};

    bossBarUpdateScheduleTask =
        this.plugin
            .getTaskSchedulingService()
            .scheduleRepeatingTask(
                () -> {
                  if (activeBossBar == null || !player.isActive() || limboPlayer == null) {
                    if (bossBarUpdateScheduleTask != null) bossBarUpdateScheduleTask.cancel();
                    if (activeBossBar != null) player.hideBossBar(activeBossBar);
                    activeBossBar = null;
                    return;
                  }

                  if (secondsRemaining[0] < 0) {
                    if (bossBarUpdateScheduleTask != null) bossBarUpdateScheduleTask.cancel();
                    if (activeBossBar != null) player.hideBossBar(activeBossBar);
                    activeBossBar = null;
                    // Optionally, trigger timeout logic here if not handled by timeoutTask
                    return;
                  }

                  String bossBarText =
                      MessageFormat.format(settingsFramework.STRINGS.BOSSBAR, secondsRemaining[0]);
                  activeBossBar.name(serializer.deserialize(bossBarText));
                  activeBossBar.progress(
                      Math.max(0.0f, (float) secondsRemaining[0] / totalAuthTimeSeconds));

                  secondsRemaining[0]--;
                },
                0,
                1000); // Run every second (1000 ms)
  }

  private void sendRegistrationPrompt() {
    Component message = serializer.deserialize(settingsFramework.STRINGS.REGISTER);
    Component title = serializer.deserialize(settingsFramework.STRINGS.REGISTER_TITLE);
    Component subtitle = serializer.deserialize(settingsFramework.STRINGS.REGISTER_SUBTITLE);

    player.sendMessage(message);
    if (!settingsFramework.STRINGS.REGISTER_TITLE.isEmpty()
        || !settingsFramework.STRINGS.REGISTER_SUBTITLE.isEmpty()) {
      player.showTitle(
          Title.title(title, subtitle, settingsFramework.CRACKED_TITLE_SETTINGS.toTimes()));
    }
  }

  private void sendLoginPrompt() {
    int attemptsLeft = settingsFramework.LOGIN_ATTEMPTS - loginAttempts;
    String loginMessageString = MessageFormat.format(settingsFramework.STRINGS.LOGIN, attemptsLeft);

    Component message = serializer.deserialize(loginMessageString);
    Component title = serializer.deserialize(settingsFramework.STRINGS.LOGIN_TITLE);
    Component subtitle =
        serializer.deserialize(
            MessageFormat.format(settingsFramework.STRINGS.LOGIN_SUBTITLE, attemptsLeft));

    player.sendMessage(message);
    if (!settingsFramework.STRINGS.LOGIN_TITLE.isEmpty()
        || !settingsFramework.STRINGS.LOGIN_SUBTITLE.isEmpty()) {
      player.showTitle(
          Title.title(title, subtitle, settingsFramework.CRACKED_TITLE_SETTINGS.toTimes()));
    }
  }

  /** Triggers the complete login sequence after authentication. */
  public void triggerSuccessfulLoginSequence() {
    Consumer<TaskEvent> eventConsumer =
        (event) -> {
          if (event.getResult() == TaskEvent.Result.CANCEL) {
            if (limboPlayer != null) limboPlayer.disconnect();
            player.disconnect(event.getReason());
            return;
          }
          if (event.getResult() != TaskEvent.Result.BYPASS) {
            cleanup();
            this.plugin.getCacheManager().cacheAuthUser(player);
            this.playerSessionService.removeAuthenticatingPlayer(player.getUsername());
            showSuccessMessages();

            RegisteredPlayer rp =
                this.playerSessionService.getRegisteredPlayer(player.getUsername());
            // Ensure LimboPlayer is available for the event
            if (limboPlayer != null && rp != null) {
              this.plugin
                  .getServer()
                  .getEventManager()
                  .fireAndForget(new PostAuthorizationEvent(taskEvent -> {}, limboPlayer, rp, ""));
            } else {
              this.logger.warn(
                  "LimboPlayer or RegisteredPlayer was null when trying to fire PostAuthorizationEvent for {}",
                  player.getUsername());
            }

            this.plugin.getAuthenticationService().updateLoginData(player);
            this.plugin.getLimboFactory().passLoginLimbo(player);
          }
        };

    RegisteredPlayer rp = this.playerSessionService.getRegisteredPlayer(player.getUsername());
    PreAuthorizationEvent preEvent =
        new PreAuthorizationEvent(eventConsumer, TaskEvent.Result.NORMAL, player, rp);
    this.plugin.getServer().getEventManager().fire(preEvent).thenAcceptAsync(eventConsumer);
  }

  private void showSuccessMessages() {
    Component message = serializer.deserialize(settingsFramework.STRINGS.LOGIN_SUCCESSFUL);
    Component title = serializer.deserialize(settingsFramework.STRINGS.LOGIN_SUCCESSFUL_TITLE);
    Component subtitle =
        serializer.deserialize(settingsFramework.STRINGS.LOGIN_SUCCESSFUL_SUBTITLE);

    player.sendMessage(message);
    if (!settingsFramework.STRINGS.LOGIN_SUCCESSFUL_TITLE.isEmpty()
        || !settingsFramework.STRINGS.LOGIN_SUCCESSFUL_SUBTITLE.isEmpty()) {
      player.showTitle(
          Title.title(title, subtitle, settingsFramework.CRACKED_TITLE_SETTINGS.toTimes()));
    }
  }

  @Override
  public void onDisconnect() {
    cleanup();
    this.playerSessionService.removeAuthenticatingPlayer(player.getUsername());
    this.logger.debug(
        "Player {} disconnected from authentication Limbo (AuthSessionHandler.onDisconnect)",
        player.getUsername());
  }

  private void cleanup() {
    if (timeoutTask != null
        && timeoutTask.status() != TaskStatus.CANCELLED
        && timeoutTask.status() != TaskStatus.FINISHED) {
      timeoutTask.cancel();
    }
    if (bossBarUpdateScheduleTask != null
        && bossBarUpdateScheduleTask.status() != TaskStatus.CANCELLED
        && bossBarUpdateScheduleTask.status() != TaskStatus.FINISHED) {
      bossBarUpdateScheduleTask.cancel();
    }
    if (activeBossBar != null) {
      player.hideBossBar(activeBossBar);
      activeBossBar = null;
    }
    if (settingsFramework.CRACKED_TITLE_SETTINGS.CLEAR_AFTER_LOGIN) {
      player.clearTitle();
    }
  }

  /**
   * Returns the underlying Player instance.
   *
   * @return The Player instance being authenticated
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Returns the associated LimboPlayer instance.
   *
   * @return The LimboPlayer instance, or null if not spawned
   */
  public LimboPlayer getLimboPlayer() {
    return limboPlayer;
  }

  /**
   * Checks if the player has been authenticated.
   *
   * @return true if authenticated, false otherwise
   */
  public boolean isAuthenticated() {
    return passwordAuthenticated;
  }

  /**
   * Sets the authentication status.
   *
   * @param authenticated true if authenticated
   */
  public void setAuthenticated(boolean authenticated) {
    this.passwordAuthenticated = authenticated;
  }

  /**
   * Returns the current number of failed login attempts.
   *
   * @return The number of failed login attempts
   */
  public int getLoginAttempts() {
    return loginAttempts;
  }

  /** Increments the login attempt counter. */
  public void incrementLoginAttempts() {
    this.loginAttempts++;
  }

  /**
   * Checks if the authentication session has expired.
   *
   * @return true if expired, false otherwise
   */
  public boolean isSessionExpired() {
    if (timeoutTask == null) return false;
    return timeoutTask.status() == TaskStatus.FINISHED && !passwordAuthenticated;
  }
}
