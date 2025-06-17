package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.AuthUnregisterEvent;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Command for players to unregister their own account. Requires password confirmation and handles
 * database deletion, cache invalidation, and disconnecting the player.
 */
public class UnregisterCommand extends RatelimitedCommand {

  private final LimboAuth plugin;
  private final Logger logger;
  private final DatabaseService databaseService;
  private final AuthenticationService authenticationService;
  private final CacheManager cacheManager;

  /**
   * Constructs the UnregisterCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param databaseService Service for database interactions.
   * @param authenticationService Service for authentication logic.
   * @param cacheManager Service for managing caches.
   * @param configManager Service for accessing configuration.
   */
  public UnregisterCommand(
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

    final String confirmKeyword = currentSettings.MAIN.CONFIRM_KEYWORD;
    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.UNREGISTER_USAGE);
    final Component notRegisteredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_REGISTERED);
    final Component crackedCommandMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.CRACKED_COMMAND);
    final Component wrongPasswordMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.WRONG_PASSWORD);
    final Component successfulMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.UNREGISTER_SUCCESSFUL);
    final Component errorOccurredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED);

    if (args.length != 2 || !confirmKeyword.equalsIgnoreCase(args[1])) {
      source.sendMessage(usageMsg);
      return;
    }

    String username = commandPlayer.getUsername();
    String usernameLowercase = username.toLowerCase(Locale.ROOT);
    RegisteredPlayer registeredPlayer =
        this.databaseService.findPlayerByLowercaseNickname(usernameLowercase);

    if (registeredPlayer == null) {
      source.sendMessage(notRegisteredMsg);
      return;
    }
    if (registeredPlayer.getHash().isEmpty()) {
      source.sendMessage(crackedCommandMsg);
      return;
    }

    if (!authenticationService.checkPassword(args[0], registeredPlayer)) {
      source.sendMessage(wrongPasswordMsg);
      return;
    }

    try {
      this.plugin.getServer().getEventManager().fireAndForget(new AuthUnregisterEvent(username));
      this.databaseService.deletePlayerByLowercaseNickname(usernameLowercase);
      this.cacheManager.removeAuthUserFromCache(username);
      commandPlayer.disconnect(successfulMsg);
    } catch (SQLRuntimeException e) {
      this.logger.error("SQL error during unregister for {}:", usernameLowercase, e);
      source.sendMessage(errorOccurredMsg);
    } catch (Exception e) {
      this.logger.error("Unexpected error during unregister for {}:", usernameLowercase, e);
      source.sendMessage(errorOccurredMsg);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return this.configManager
        .getCommandPermissionState()
        .UNREGISTER
        .hasPermission(invocation.source(), "limboauth.commands.unregister");
  }
}
