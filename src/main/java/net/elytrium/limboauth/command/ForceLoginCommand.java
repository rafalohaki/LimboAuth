package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Collectors;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.PlayerSessionService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Admin command to forcibly log in a player who is currently in the authentication process. This
 * bypasses the normal password/2FA checks for that player.
 */
public class ForceLoginCommand extends RatelimitedCommand {

  private final PlayerSessionService playerSessionService;
  private final LimboAuth plugin;
  private final Logger logger;

  /**
   * Constructs the ForceLoginCommand.
   *
   * @param plugin The main LimboAuth plugin instance, used for logger access.
   * @param playerSessionService Service for managing player authentication sessions.
   * @param configManager Service for accessing configuration.
   */
  public ForceLoginCommand(
      LimboAuth plugin, PlayerSessionService playerSessionService, ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
    this.playerSessionService = playerSessionService;
    this.logger = this.plugin.getLogger();
  }

  @Override
  protected void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent) {
    Settings currentSettings = this.configManager.getSettings();
    Serializer currentSerializer = this.configManager.getSerializer();

    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.FORCE_LOGIN_USAGE);
    final String unknownPlayerFormat = currentSettings.MAIN.STRINGS.FORCE_LOGIN_UNKNOWN_PLAYER;
    final String successfulFormat = currentSettings.MAIN.STRINGS.FORCE_LOGIN_SUCCESSFUL;
    final Component errorOccurredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED);

    if (args.length != 1) {
      source.sendMessage(usageMsg);
      return;
    }

    String nickname = args[0];
    AuthSessionHandler handler = this.playerSessionService.getAuthenticatingPlayer(nickname);

    if (handler == null) {
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(unknownPlayerFormat, nickname)));
      return;
    }

    try {
      // Poprzednio było finishLogin(), ale triggerSuccessfulLoginSequence() jest bardziej
      // odpowiednie,
      // bo obsługuje eventy i inne kroki finalizacji logowania.
      handler.triggerSuccessfulLoginSequence();
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(successfulFormat, nickname)));
    } catch (Exception e) {
      this.logger.error("Error during force login for {}:", nickname, e);
      source.sendMessage(errorOccurredMsg);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return this.configManager
        .getCommandPermissionState()
        .FORCE_LOGIN
        .hasPermission(invocation.source(), "limboauth.admin.forcelogin");
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    if (invocation.arguments().length > 1) {
      return super.suggest(invocation);
    }
    String currentArg =
        invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase();
    return this.playerSessionService.getAuthenticatingPlayers().keySet().stream()
        .filter(username -> username.toLowerCase().startsWith(currentArg))
        .collect(Collectors.toList());
  }
}
