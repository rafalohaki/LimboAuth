package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.ChangePasswordEvent;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Command for players to change their own password. Handles checks for registration, current
 * password (if configured), new password validity, and updates the database and cache.
 */
public class ChangePasswordCommand extends RatelimitedCommand {

  private final LimboAuth plugin; // For event firing and logger
  private final Logger logger;
  private final DatabaseService databaseService;
  private final AuthenticationService authenticationService;
  private final CacheManager cacheManager;
  // ConfigManager is available via super.configManager

  private final boolean needOldPassConfigValue;

  /**
   * Constructs the ChangePasswordCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param databaseService Service for database interactions.
   * @param authenticationService Service for authentication logic.
   * @param cacheManager Service for managing caches.
   * @param configManager Service for accessing configuration.
   */
  public ChangePasswordCommand(
      LimboAuth plugin,
      DatabaseService databaseService,
      AuthenticationService authenticationService,
      CacheManager cacheManager,
      ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
    this.databaseService = databaseService;
    this.authenticationService = authenticationService;
    this.cacheManager = cacheManager;
    this.needOldPassConfigValue =
        this.configManager.getSettings().MAIN.CHANGE_PASSWORD_NEED_OLD_PASSWORD;
  }

  @Override
  protected void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent) {
    if (!(source instanceof Player)) {
      source.sendMessage(
          configManager
              .getSerializer()
              .deserialize(configManager.getSettings().MAIN.STRINGS.NOT_PLAYER));
      return;
    }

    Player commandPlayer = (Player) source;
    Settings currentSettings = this.configManager.getSettings();
    Serializer currentSerializer = this.configManager.getSerializer();

    final Component notRegisteredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_REGISTERED);
    final Component wrongPasswordMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.WRONG_PASSWORD);
    final Component successfulMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.CHANGE_PASSWORD_SUCCESSFUL);
    final Component errorOccurredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED);
    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.CHANGE_PASSWORD_USAGE);
    final Component crackedCommandMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.CRACKED_COMMAND);
    final Component passTooShortMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_TOO_SHORT);
    final Component passTooLongMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_TOO_LONG);
    final Component passUnsafeMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_UNSAFE);
    // Assuming PASSWORD_SAME_AS_OLD is added to Settings.java -> MAIN.STRINGS
    final Component passSameAsOldMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.PASSWORD_SAME_AS_OLD);

    String usernameLowercase = commandPlayer.getUsername().toLowerCase(Locale.ROOT);
    RegisteredPlayer registeredPlayer =
        this.databaseService.findPlayerByLowercaseNickname(usernameLowercase);

    if (registeredPlayer == null) {
      source.sendMessage(notRegisteredMsg);
      return;
    }

    if (registeredPlayer.getHash().isEmpty()) { // Premium accounts cannot use this
      source.sendMessage(crackedCommandMsg);
      return;
    }

    boolean effectivelyNeedOldPass = this.needOldPassConfigValue;
    int expectedArgs = effectivelyNeedOldPass ? 2 : 1;

    if (args.length < expectedArgs) {
      source.sendMessage(usageMsg);
      return;
    }

    String oldPasswordAttempt = effectivelyNeedOldPass ? args[0] : null;
    String newPassword = effectivelyNeedOldPass ? args[1] : args[0];

    if (effectivelyNeedOldPass) {
      if (oldPasswordAttempt == null
          || !authenticationService.checkPassword(oldPasswordAttempt, registeredPlayer)) {
        source.sendMessage(wrongPasswordMsg);
        return;
      }
      if (oldPasswordAttempt.equals(newPassword)) {
        source.sendMessage(passSameAsOldMsg);
        return;
      }
    }

    if (newPassword.length() < currentSettings.MAIN.MIN_PASSWORD_LENGTH) {
      source.sendMessage(passTooShortMsg);
      return;
    }
    if (newPassword.length() > currentSettings.MAIN.MAX_PASSWORD_LENGTH) {
      source.sendMessage(passTooLongMsg);
      return;
    }
    if (currentSettings.MAIN.CHECK_PASSWORD_STRENGTH
        && this.configManager.unsafePasswords.contains(newPassword)) {
      source.sendMessage(passUnsafeMsg);
      return;
    }

    try {
      final String oldActualHash = registeredPlayer.getHash();
      registeredPlayer.setPassword(newPassword); // Hashes and updates tokenIssuedAt
      this.databaseService.updatePlayer(registeredPlayer);

      this.cacheManager.removeAuthUserFromCache(commandPlayer.getUsername());

      this.plugin
          .getServer()
          .getEventManager()
          .fireAndForget(
              new ChangePasswordEvent(
                  registeredPlayer,
                  oldPasswordAttempt,
                  oldActualHash,
                  newPassword,
                  registeredPlayer.getHash()));

      source.sendMessage(successfulMsg);
    } catch (SQLRuntimeException e) {
      this.logger.error("SQL error changing password for {}:", commandPlayer.getUsername(), e);
      source.sendMessage(errorOccurredMsg);
    } catch (Exception e) {
      this.logger.error(
          "Unexpected error changing password for {}:", commandPlayer.getUsername(), e);
      source.sendMessage(errorOccurredMsg);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return super.configManager
        .getCommandPermissionState()
        .CHANGE_PASSWORD
        .hasPermission(invocation.source(), "limboauth.commands.changepassword");
  }
}
