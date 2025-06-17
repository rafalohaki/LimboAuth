package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.ConfigManager;
import net.kyori.adventure.text.Component;

/**
 * Command for players to destroy their current authenticated session. This forces them to log in
 * again upon their next connection or action requiring authentication.
 */
public class DestroySessionCommand extends RatelimitedCommand {

  private final CacheManager cacheManager;

  // ConfigManager is available via super.configManager

  /**
   * Constructs the DestroySessionCommand.
   *
   * @param plugin The main LimboAuth plugin instance (can be used for plugin-specific features if
   *     needed).
   * @param cacheManager Service for managing caches, used here to remove the session.
   * @param configManager Service for accessing configuration, used for messages and permissions.
   */
  public DestroySessionCommand(
      LimboAuth plugin, CacheManager cacheManager, ConfigManager configManager) {
    super(configManager); // Pass ConfigManager to the superclass
    this.cacheManager = cacheManager;
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
    this.cacheManager.removeAuthUserFromCache(player.getUsername());
    player.sendMessage(
        configManager
            .getSerializer()
            .deserialize(this.configManager.getSettings().MAIN.STRINGS.DESTROY_SESSION_SUCCESSFUL));
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    // Access configManager from the superclass
    return super.configManager
        .getSettings()
        .MAIN
        .COMMAND_PERMISSION_STATE
        .DESTROY_SESSION
        .hasPermission(invocation.source(), "limboauth.commands.destroysession");
  }
}
