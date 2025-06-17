package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Command for players to mark their account as "premium". This typically involves verifying their
 * username against Mojang's API and, if successful, clearing their password hash in the database,
 * effectively making their account online-mode only for this server's authentication system.
 */
public class PremiumCommand extends RatelimitedCommand {

  private final Logger logger;
  private final DatabaseService databaseService;
  private final AuthenticationService authenticationService;
  private final CacheManager cacheManager;
  private final LimboAuth plugin; // For disconnecting player

  /**
   * Constructs the PremiumCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param databaseService Service for database interactions.
   * @param authenticationService Service for authentication logic, including premium checks.
   * @param cacheManager Service for managing caches.
   * @param configManager Service for accessing configuration.
   */
  public PremiumCommand(
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
    final Component notRegisteredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_REGISTERED);
    final Component alreadyPremiumMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ALREADY_PREMIUM);
    final Component successfulMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.PREMIUM_SUCCESSFUL);
    final Component errorOccurredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED);
    final Component notPremiumOnlineMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_PREMIUM);
    final Component wrongPasswordMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.WRONG_PASSWORD);
    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.PREMIUM_USAGE);

    if (args.length != 2 || !confirmKeyword.equalsIgnoreCase(args[1])) {
      source.sendMessage(usageMsg);
      return;
    }

    String usernameLowercase = commandPlayer.getUsername().toLowerCase(Locale.ROOT);
    RegisteredPlayer registeredPlayer =
        this.databaseService.findPlayerByLowercaseNickname(usernameLowercase);

    if (registeredPlayer == null) {
      source.sendMessage(notRegisteredMsg);
      return;
    }
    if (registeredPlayer.getHash().isEmpty()) { // Already marked as premium (no password hash)
      source.sendMessage(alreadyPremiumMsg);
      return;
    }

    if (!authenticationService.checkPassword(args[0], registeredPlayer)) {
      source.sendMessage(wrongPasswordMsg);
      return;
    }

    // Check with external Mojang API if the username is actually premium
    LimboAuth.PremiumResponse premiumCheck =
        this.authenticationService.isPremiumExternal(commandPlayer.getUsername());

    if (premiumCheck.getState() == LimboAuth.PremiumState.PREMIUM_USERNAME) {
      try {
        registeredPlayer.setHash(""); // Clear password to mark as premium
        if (premiumCheck.getUuid() != null) { // Save the Mojang UUID
          registeredPlayer.setPremiumUuid(premiumCheck.getUuid());
          if (currentSettings.MAIN.SAVE_UUID) { // If SAVE_UUID, also set the main UUID
            registeredPlayer.setUuid(premiumCheck.getUuid().toString());
          }
        }
        this.databaseService.updatePlayer(registeredPlayer);
        this.cacheManager.removeAuthUserFromCache(
            commandPlayer.getUsername()); // Invalidate session
        commandPlayer.disconnect(successfulMsg); // Disconnect to force re-login with premium status
      } catch (SQLRuntimeException e) {
        this.logger.error("SQL error converting {} to premium:", usernameLowercase, e);
        source.sendMessage(errorOccurredMsg);
      }
    } else {
      // Handle other states like CRACKED, UNKNOWN, RATE_LIMIT, ERROR
      source.sendMessage(notPremiumOnlineMsg);
      if (premiumCheck.getState() == LimboAuth.PremiumState.RATE_LIMIT) {
        source.sendMessage(
            currentSerializer.deserialize(currentSettings.MAIN.STRINGS.MOJANG_API_RATE_LIMITED));
      } else if (premiumCheck.getState() == LimboAuth.PremiumState.ERROR) {
        source.sendMessage(
            currentSerializer.deserialize(currentSettings.MAIN.STRINGS.MOJANG_API_ERROR));
      }
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return super.configManager
        .getCommandPermissionState()
        .PREMIUM
        .hasPermission(invocation.source(), "limboauth.commands.premium");
  }
}
