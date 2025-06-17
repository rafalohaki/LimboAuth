package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Admin command to forcibly register a new player account. Handles nickname validation, password
 * validation, and database creation.
 */
public class ForceRegisterCommand extends RatelimitedCommand {

  private final DatabaseService databaseService;
  private final LimboAuth plugin;
  // Usunięto pole nicknameValidationPattern, będziemy pobierać z ConfigManager
  private final Logger logger;

  /**
   * Constructs the ForceRegisterCommand.
   *
   * @param plugin The main LimboAuth plugin instance, used for logger access.
   * @param databaseService Service for database interactions.
   * @param configManager Service for accessing configuration.
   */
  public ForceRegisterCommand(
      LimboAuth plugin, DatabaseService databaseService, ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
    this.databaseService = databaseService;
    this.logger = this.plugin.getLogger();
  }

  @Override
  protected void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent) {
    Settings currentSettings = this.configManager.getSettings();
    Serializer currentSerializer = this.configManager.getSerializer();
    Pattern nicknamePattern =
        this.configManager.getNicknameValidationPattern(); // Pobierz pattern z ConfigManager

    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.FORCE_REGISTER_USAGE);
    final Component incorrectNicknameMsg =
        currentSerializer.deserialize(
            currentSettings.MAIN.STRINGS.FORCE_REGISTER_INCORRECT_NICKNAME);
    final Component takenNicknameMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.FORCE_REGISTER_TAKEN_NICKNAME);
    final String successfulFormat = currentSettings.MAIN.STRINGS.FORCE_REGISTER_SUCCESSFUL;
    final String notSuccessfulFormat = currentSettings.MAIN.STRINGS.FORCE_REGISTER_NOT_SUCCESSFUL;
    final Component errorOccurredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED);

    if (args.length != 2) {
      source.sendMessage(usageMsg);
      return;
    }

    String nickname = args[0];
    String password = args[1];

    if (!nicknamePattern.matcher(nickname).matches()) { // Użyj pobranego patternu
      source.sendMessage(incorrectNicknameMsg);
      return;
    }

    String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    if (databaseService.findPlayerByLowercaseNickname(lowercaseNickname) != null) {
      source.sendMessage(takenNicknameMsg);
      return;
    }

    if (password.length() < currentSettings.MAIN.MIN_PASSWORD_LENGTH) {
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_TOO_SHORT));
      return;
    }
    if (password.length() > currentSettings.MAIN.MAX_PASSWORD_LENGTH) {
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_TOO_LONG));
      return;
    }
    // Użyj publicznego pola unsafePasswords z ConfigManager
    if (currentSettings.MAIN.CHECK_PASSWORD_STRENGTH
        && this.configManager.unsafePasswords.contains(password)) {
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_UNSAFE));
      return;
    }

    try {
      RegisteredPlayer player = new RegisteredPlayer(nickname, "", "");
      player.setPassword(password);
      this.databaseService.createPlayer(player);
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(successfulFormat, nickname)));
    } catch (SQLRuntimeException e) {
      this.logger.error("SQL error during force register for {}:", nickname, e);
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(notSuccessfulFormat, nickname)));
    } catch (Exception e) {
      this.logger.error("Unexpected error during force register for {}:", nickname, e);
      source.sendMessage(errorOccurredMsg);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return this.configManager
        .getCommandPermissionState()
        .FORCE_REGISTER
        .hasPermission(invocation.source(), "limboauth.admin.forceregister");
  }
}
