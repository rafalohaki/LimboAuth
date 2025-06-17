package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.commons.velocity.commands.SuggestUtils;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.ChangePasswordEvent;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Admin command to forcibly change a player's password. Handles checks for player registration,
 * updates database, invalidates session, and notifies the target player if online.
 */
public class ForceChangePasswordCommand extends RatelimitedCommand {

  private final LimboAuth plugin; // For event manager & logger
  private final Logger logger;
  private final ProxyServer server; // For player suggestions and messaging
  private final DatabaseService databaseService;
  private final CacheManager cacheManager;

  // ConfigManager is available via super.configManager

  /**
   * Constructs the ForceChangePasswordCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param server The ProxyServer instance.
   * @param configManager Service for accessing configuration.
   * @param databaseService Service for database interactions.
   * @param cacheManager Service for managing caches.
   */
  public ForceChangePasswordCommand(
      LimboAuth plugin,
      ProxyServer server,
      ConfigManager configManager,
      DatabaseService databaseService,
      CacheManager cacheManager) {
    super(configManager);
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
    this.server = server;
    this.databaseService = databaseService;
    this.cacheManager = cacheManager;
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    return SuggestUtils.suggestPlayers(this.server, invocation.arguments(), 0);
  }

  @Override
  protected void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent) {
    Settings currentSettings = this.configManager.getSettings();
    Serializer currentSerializer = this.configManager.getSerializer();

    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_USAGE);

    if (args.length != 2) {
      source.sendMessage(usageMsg);
      return;
    }

    String targetNickname = args[0];
    String targetNicknameLowercased = targetNickname.toLowerCase(Locale.ROOT);
    String newPassword = args[1];

    final Component notRegisteredMsg =
        currentSerializer.deserialize(
            MessageFormat.format(
                currentSettings.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_REGISTERED, targetNickname));
    final Component notSuccessfulMsg =
        currentSerializer.deserialize(
            MessageFormat.format(
                currentSettings.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL, targetNickname));
    final Component successfulMsg =
        currentSerializer.deserialize(
            MessageFormat.format(
                currentSettings.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_SUCCESSFUL, targetNickname));
    final Component messageToPlayer =
        currentSerializer.deserialize(
            MessageFormat.format(
                currentSettings.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_MESSAGE, newPassword));

    try {
      RegisteredPlayer registeredPlayer =
          this.databaseService.findPlayerByLowercaseNickname(targetNicknameLowercased);

      if (registeredPlayer == null) {
        source.sendMessage(notRegisteredMsg);
        return;
      }
      if (registeredPlayer.getHash().isEmpty() && !currentSettings.MAIN.ONLINE_MODE_NEED_AUTH) {
        source.sendMessage(
            currentSerializer.deserialize(
                MessageFormat.format(
                    currentSettings.MAIN.STRINGS.PLAYER_IS_PREMIUM_NO_PASS_CHANGE,
                    targetNickname)));
        return;
      }

      final String oldHash = registeredPlayer.getHash();
      registeredPlayer.setPassword(newPassword); // This handles hashing
      this.databaseService.updatePlayer(registeredPlayer);

      this.cacheManager.removeAuthUserFromCache(targetNickname); // Invalidate session

      this.server
          .getPlayer(targetNickname)
          .ifPresent(player -> player.sendMessage(messageToPlayer));

      this.plugin
          .getServer()
          .getEventManager()
          .fireAndForget(
              new ChangePasswordEvent(
                  registeredPlayer, null, oldHash, newPassword, registeredPlayer.getHash()));

      source.sendMessage(successfulMsg);

    } catch (SQLRuntimeException e) {
      this.logger.error("SQL error forcing password change for {}:", targetNickname, e);
      source.sendMessage(notSuccessfulMsg);
    } catch (Exception e) {
      this.logger.error("Unexpected error forcing password change for {}:", targetNickname, e);
      source.sendMessage(notSuccessfulMsg);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return super.configManager
        .getSettings()
        .MAIN
        .COMMAND_PERMISSION_STATE
        .FORCE_CHANGE_PASSWORD
        .hasPermission(invocation.source(), "limboauth.admin.forcechangepassword");
  }
}
