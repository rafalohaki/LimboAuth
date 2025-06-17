// File: LimboAuth/src/main/java/net/elytrium/limboauth/command/LoginCommand.java
package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.text.MessageFormat;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.PlayerSessionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

/**
 * Command for players to log into their account. Handles password verification and TOTP if enabled.
 */
public class LoginCommand extends RatelimitedCommand {

  private final LimboAuth plugin;
  private final Logger logger;
  private final AuthenticationService authenticationService;
  private final PlayerSessionService playerSessionService;

  public LoginCommand(
      LimboAuth plugin,
      AuthenticationService authenticationService,
      PlayerSessionService playerSessionService,
      ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
    this.authenticationService = authenticationService;
    this.playerSessionService = playerSessionService;
  }

  @Override
  protected void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent) {
    if (!(source instanceof Player)) {
      source.sendMessage(
          configManager
              .getSerializer()
              .deserialize(this.configManager.getSettings().MAIN.STRINGS.NOT_PLAYER));
      return;
    }

    Player player = (Player) source;
    Settings currentSettings = this.configManager.getSettings();
    Serializer currentSerializer = this.configManager.getSerializer();

    AuthSessionHandler handler =
        this.playerSessionService.getAuthenticatingPlayer(player.getUsername());
    if (handler == null) {
      player.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED));
      this.logger.warn(
          "Player {} tried to use /login but no AuthSessionHandler was found.",
          player.getUsername());
      return;
    }

    if (args.length == 0) {
      int attemptsLeft = currentSettings.MAIN.LOGIN_ATTEMPTS - handler.getLoginAttempts();
      source.sendMessage(
          currentSerializer.deserialize(
              MessageFormat.format(currentSettings.MAIN.STRINGS.LOGIN, attemptsLeft)));
      return;
    }

    String passwordOrCode = args[0];
    String usernameLowercase = player.getUsername().toLowerCase(Locale.ROOT);

    try {
      RegisteredPlayer registeredPlayer =
          this.plugin.getDatabaseService().findPlayerByLowercaseNickname(usernameLowercase);

      if (registeredPlayer == null) {
        source.sendMessage(
            currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_REGISTERED));
        return;
      }

      if (registeredPlayer.getHash().isEmpty()) {
        source.sendMessage(
            currentSerializer.deserialize(currentSettings.MAIN.STRINGS.CRACKED_COMMAND));
        return;
      }

      if (handler.isSessionExpired()) {
        player.disconnect(currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TIMES_UP));
        return;
      }

      // If password not yet authenticated, this input is the password
      if (!handler.isAuthenticated()) {
        if (!authenticationService.checkPassword(passwordOrCode, registeredPlayer)) {
          handler.incrementLoginAttempts();
          int attemptsLeft = currentSettings.MAIN.LOGIN_ATTEMPTS - handler.getLoginAttempts();

          if (attemptsLeft <= 0) {
            this.plugin
                .getCacheManager()
                .incrementBruteforceAttempts(player.getRemoteAddress().getAddress());
            player.disconnect(
                currentSerializer.deserialize(
                    currentSettings.MAIN.STRINGS.LOGIN_WRONG_PASSWORD_KICK));
          } else {
            source.sendMessage(
                currentSerializer.deserialize(
                    MessageFormat.format(
                        currentSettings.MAIN.STRINGS.LOGIN_WRONG_PASSWORD, attemptsLeft)));
          }
          return;
        }
        // Password correct
        handler.setAuthenticated(true);
        this.playerSessionService.resetLoginAttempts(
            player.getUsername()); // Reset attempts on correct password

        // If TOTP is enabled and required, prompt for it
        if (currentSettings.MAIN.ENABLE_TOTP
            && registeredPlayer.getTotpToken() != null
            && !registeredPlayer.getTotpToken().isEmpty()) {
          // If command was just /login , now prompt for TOTP
          if (args.length == 1) {
            player.sendMessage(currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP));
            if (currentSettings.MAIN.STRINGS.TOTP_TITLE != null
                && !currentSettings.MAIN.STRINGS.TOTP_TITLE.isEmpty()) {
              Component mainTitle =
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_TITLE);
              Component subTitle =
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_SUBTITLE);
              Title.Times times =
                  currentSettings.MAIN.PREMIUM_TITLE_SETTINGS
                      .toTimes(); // Or a specific TOTP_TITLE_SETTINGS
              player.showTitle(Title.title(mainTitle, subTitle, times));
            }
            return; // Wait for player to send TOTP code with /login
          }
          // If command was /login , and TOTP is enabled, then args[1] is TOTP code
          String totpCode = args[1];
          if (AuthSessionHandler.TOTP_CODE_VERIFIER.isValidCode(
              registeredPlayer.getTotpToken(), totpCode)) {
            this.logger.info(
                "Player {} successfully authenticated with password and TOTP.",
                player.getUsername());
            handler.triggerSuccessfulLoginSequence();
          } else {
            handler.setAuthenticated(false); // Reset password auth status because TOTP failed
            handler.incrementLoginAttempts();
            int attemptsLeft = currentSettings.MAIN.LOGIN_ATTEMPTS - handler.getLoginAttempts();
            if (attemptsLeft <= 0) {
              player.disconnect(
                  currentSerializer.deserialize(
                      currentSettings.MAIN.STRINGS.LOGIN_WRONG_PASSWORD_KICK));
            } else {
              player.sendMessage(
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_WRONG));
            }
          }
        } else {
          // No TOTP, login successful
          this.logger.info(
              "Player {} successfully authenticated with password.", player.getUsername());
          handler.triggerSuccessfulLoginSequence();
        }
      } else { // Password already authenticated, this input must be a TOTP code
        if (currentSettings.MAIN.ENABLE_TOTP
            && registeredPlayer.getTotpToken() != null
            && !registeredPlayer.getTotpToken().isEmpty()) {
          if (AuthSessionHandler.TOTP_CODE_VERIFIER.isValidCode(
              registeredPlayer.getTotpToken(), passwordOrCode)) {
            this.logger.info(
                "Player {} successfully authenticated with TOTP.", player.getUsername());
            handler.triggerSuccessfulLoginSequence();
          } else {
            handler.incrementLoginAttempts();
            int attemptsLeft = currentSettings.MAIN.LOGIN_ATTEMPTS - handler.getLoginAttempts();
            if (attemptsLeft <= 0) {
              player.disconnect(
                  currentSerializer.deserialize(
                      currentSettings.MAIN.STRINGS.LOGIN_WRONG_PASSWORD_KICK));
            } else {
              player.sendMessage(
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_WRONG));
              player.sendMessage(
                  currentSerializer.deserialize(
                      currentSettings.MAIN.STRINGS.TOTP)); // Re-prompt for TOTP
              if (currentSettings.MAIN.STRINGS.TOTP_TITLE != null
                  && !currentSettings.MAIN.STRINGS.TOTP_TITLE.isEmpty()) {
                Component mainTitle =
                    currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_TITLE);
                Component subTitle =
                    currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_SUBTITLE);
                Title.Times times = currentSettings.MAIN.PREMIUM_TITLE_SETTINGS.toTimes();
                player.showTitle(Title.title(mainTitle, subTitle, times));
              }
            }
          }
        } else {
          // Should not happen: password authenticated but TOTP not enabled/setup, yet received more
          // input
          this.logger.warn(
              "Player {} sent additional input after password authentication, but TOTP is not active.",
              player.getUsername());
          handler.triggerSuccessfulLoginSequence(); // Proceed as if login was complete
        }
      }

    } catch (Exception e) {
      this.logger.error("Error during login for {}:", player.getUsername(), e);
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED));
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return true; // Login is generally public
  }
}
