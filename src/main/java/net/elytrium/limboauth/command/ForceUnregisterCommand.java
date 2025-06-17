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
import net.elytrium.limboauth.event.AuthUnregisterEvent;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Admin command to forcibly unregister a player's account. Deletes the player's data from the
 * database, invalidates their session, and kicks them if they are online.
 */
public class ForceUnregisterCommand extends RatelimitedCommand {

  private final LimboAuth plugin; // Dla EventManager
  private final ProxyServer server;
  private final DatabaseService databaseService;
  private final CacheManager cacheManager;
  private final Logger logger;

  /**
   * Constructs the ForceUnregisterCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param server The ProxyServer instance.
   * @param databaseService Service for database interactions.
   * @param cacheManager Service for managing caches.
   * @param configManager Service for accessing configuration.
   */
  public ForceUnregisterCommand(
      LimboAuth plugin,
      ProxyServer server,
      DatabaseService databaseService,
      CacheManager cacheManager,
      ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
    this.server = server;
    this.databaseService = databaseService;
    this.cacheManager = cacheManager;
    this.logger = this.plugin.getLogger();
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
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.FORCE_UNREGISTER_USAGE);
    final Component kickMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.FORCE_UNREGISTER_KICK);
    final String successfulFormat = currentSettings.MAIN.STRINGS.FORCE_UNREGISTER_SUCCESSFUL;
    final String notSuccessfulFormat = currentSettings.MAIN.STRINGS.FORCE_UNREGISTER_NOT_SUCCESSFUL;

    if (args.length != 1) {
      source.sendMessage(usageMsg);
      return;
    }

    String playerNick = args[0];
    String usernameLowercased = playerNick.toLowerCase(Locale.ROOT);

    try {
      if (databaseService.findPlayerByLowercaseNickname(usernameLowercased) == null) {
        source.sendMessage(
            currentSerializer.deserialize(
                MessageFormat.format(notSuccessfulFormat, playerNick)
                    + " (Player not found in database)"));
        return;
      }

      this.plugin.getServer().getEventManager().fireAndForget(new AuthUnregisterEvent(playerNick));
      this.databaseService.deletePlayerByLowercaseNickname(usernameLowercased);
      this.cacheManager.removeAuthUserFromCache(playerNick);
      this.server.getPlayer(playerNick).ifPresent(player -> player.disconnect(kickMsg));
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(successfulFormat, playerNick)));
    } catch (SQLRuntimeException e) {
      this.logger.error("SQL error during force unregister for {}:", playerNick, e);
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(notSuccessfulFormat, playerNick)));
    } catch (Exception e) {
      this.logger.error("Unexpected error during force unregister for {}:", playerNick, e);
      source.sendMessage(
          currentSerializer.deserialize(MessageFormat.format(notSuccessfulFormat, playerNick)));
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return this.configManager
        .getCommandPermissionState()
        .FORCE_UNREGISTER
        .hasPermission(invocation.source(), "limboauth.admin.forceunregister");
  }
}
