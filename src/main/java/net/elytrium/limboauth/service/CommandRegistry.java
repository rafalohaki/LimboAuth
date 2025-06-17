// File: LimboAuth/src/main/java/net/elytrium/limboauth/service/CommandRegistry.java
package net.elytrium.limboauth.service;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.List;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.command.*;
import org.slf4j.Logger;

/** Service responsible for registering and managing plugin commands. */
public class CommandRegistry {

  private final LimboAuth plugin;
  private final Logger logger;
  private final CommandManager commandManager;
  private final DatabaseService databaseService;
  private final AuthenticationService authenticationService;
  private final PlayerSessionService playerSessionService;
  private final ConfigManager configManager;
  private final CacheManager cacheManager;
  private final ProxyServer server;

  private final List<RegisteredCommandInfo> registeredCommands = new ArrayList<>();

  public CommandRegistry(
      LimboAuth plugin,
      Logger logger,
      CommandManager commandManager,
      DatabaseService databaseService,
      AuthenticationService authenticationService,
      PlayerSessionService playerSessionService,
      ConfigManager configManager,
      CacheManager cacheManager,
      ProxyServer server) {
    this.plugin = plugin;
    this.logger = logger;
    this.commandManager = commandManager;
    this.databaseService = databaseService;
    this.authenticationService = authenticationService;
    this.playerSessionService = playerSessionService;
    this.configManager = configManager;
    this.cacheManager = cacheManager;
    this.server = server;
  }

  /** Initializes and registers all commands. */
  public void initialize() {
    registerAllCommands();
  }

  public void registerAllCommands() {
    // Register main plugin command
    registerCommand("limboauth", new LimboAuthCommand(plugin, configManager));

    // Register user commands
    registerCommandWithAliases(
        this.configManager.getSettings().MAIN.REGISTER_COMMAND,
        new RegisterCommand(
            plugin, databaseService, authenticationService, cacheManager, configManager));
    registerCommandWithAliases(
        this.configManager.getSettings().MAIN.LOGIN_COMMAND,
        new LoginCommand(plugin, authenticationService, playerSessionService, configManager));
    registerCommandWithAliases(
        this.configManager.getSettings().MAIN.TOTP_COMMAND,
        new TotpCommand(plugin, databaseService, authenticationService, configManager));

    // Register utility commands
    registerCommand(
        "changepassword",
        new ChangePasswordCommand(
            plugin, databaseService, authenticationService, cacheManager, configManager));
    registerCommand(
        "destroysession", new DestroySessionCommand(plugin, cacheManager, configManager));
    registerCommand(
        "unregister",
        new UnregisterCommand(
            plugin, databaseService, authenticationService, cacheManager, configManager));
    registerCommand(
        "premium",
        new PremiumCommand(
            plugin, databaseService, authenticationService, cacheManager, configManager));

    // Register admin commands
    registerCommand(
        "forceregister", new ForceRegisterCommand(plugin, databaseService, configManager));
    registerCommand(
        "forcelogin", new ForceLoginCommand(plugin, playerSessionService, configManager));
    registerCommand(
        "forcechangepassword",
        new ForceChangePasswordCommand(
            plugin, server, configManager, databaseService, cacheManager));
    registerCommand(
        "forceunregister",
        new ForceUnregisterCommand(plugin, server, databaseService, cacheManager, configManager));

    this.logger.info("Registered {} commands successfully.", registeredCommands.size());
  }

  private void registerCommand(String baseAlias, SimpleCommand command) {
    commandManager.register(commandManager.metaBuilder(baseAlias).build(), command);
    registeredCommands.add(new RegisteredCommandInfo(baseAlias, List.of(baseAlias), command));
    this.logger.debug("Registered command: {}", baseAlias);
  }

  private void registerCommandWithAliases(List<String> aliases, SimpleCommand command) {
    if (aliases.isEmpty()) {
      this.logger.warn("Attempted to register command with empty aliases list");
      return;
    }

    String primaryAlias = aliases.get(0);
    String[] additionalAliases = aliases.subList(1, aliases.size()).toArray(new String[0]);

    commandManager.register(
        commandManager.metaBuilder(primaryAlias).aliases(additionalAliases).build(), command);

    registeredCommands.add(new RegisteredCommandInfo(primaryAlias, aliases, command));
    this.logger.debug("Registered command '{}' with aliases: {}", primaryAlias, aliases);
  }

  public void unregisterAllCommands() {
    for (RegisteredCommandInfo commandInfo :
        new ArrayList<>(registeredCommands)) { // Iterate over a copy
      commandManager.unregister(commandInfo.primaryAlias);
    }
    registeredCommands.clear();
    this.logger.info("Unregistered all commands.");
  }

  private static class RegisteredCommandInfo {
    public final String primaryAlias;
    public final List<String> allAliases;
    public final SimpleCommand command;

    public RegisteredCommandInfo(
        String primaryAlias, List<String> allAliases, SimpleCommand command) {
      this.primaryAlias = primaryAlias;
      this.allAliases = allAliases;
      this.command = command;
    }
  }
}
