// File: LimboAuth/src/main/java/net/elytrium/limboauth/command/RegisterCommand.java
package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.function.Consumer;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.PostRegisterEvent;
import net.elytrium.limboauth.event.PreRegisterEvent;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Command for players to register a new account. Handles password validation, database creation,
 * and automatic login.
 */
public class RegisterCommand extends RatelimitedCommand {

  private final LimboAuth plugin;
  private final Logger logger;
  private final DatabaseService databaseService;
  private final AuthenticationService authenticationService;
  private final CacheManager cacheManager;

  public RegisterCommand(
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
              .deserialize(this.configManager.getSettings().MAIN.STRINGS.NOT_PLAYER));
      return;
    }

    Player player = (Player) source;
    Settings currentSettings = this.configManager.getSettings();
    Serializer currentSerializer = this.configManager.getSerializer();

    if (currentSettings.MAIN.DISABLE_REGISTRATIONS) {
      player.disconnect(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTRATIONS_DISABLED_KICK));
      return;
    }

    String usernameLowercase = player.getUsername().toLowerCase(Locale.ROOT);
    RegisteredPlayer existingPlayer =
        this.databaseService.findPlayerByLowercaseNickname(usernameLowercase);

    if (existingPlayer != null) {
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_REGISTERED));
      return;
    }

    boolean needRepeatPassword = currentSettings.MAIN.REGISTER_NEED_REPEAT_PASSWORD;
    int expectedArgs = needRepeatPassword ? 2 : 1;

    String usageMessageString = currentSettings.MAIN.STRINGS.REGISTER;

    if (args.length < expectedArgs) {
      source.sendMessage(currentSerializer.deserialize(usageMessageString));
      return;
    }

    String password = args[0];
    if (needRepeatPassword && !password.equals(args[1])) {
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_DIFFERENT_PASSWORDS));
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

    if (currentSettings.MAIN.CHECK_PASSWORD_STRENGTH
        && this.configManager.unsafePasswords.contains(password)) {
      source.sendMessage(
          currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_PASSWORD_UNSAFE));
      return;
    }

    Consumer<TaskEvent> eventConsumer =
        (event) -> {
          if (event.getResult() == TaskEvent.Result.CANCEL) {
            player.disconnect(event.getReason());
            return;
          }
          if (event.getResult() != TaskEvent.Result.BYPASS) {
            try {
              RegisteredPlayer newPlayer = new RegisteredPlayer(player);
              newPlayer.setPassword(password);
              newPlayer.setIP(player.getRemoteAddress().getAddress().getHostAddress());
              newPlayer.setRegDate(System.currentTimeMillis());
              newPlayer.setLoginIp(player.getRemoteAddress().getAddress().getHostAddress());
              newPlayer.setLoginDate(System.currentTimeMillis());

              this.databaseService.createPlayer(newPlayer);
              this.cacheManager.cacheAuthUser(player);

              this.plugin
                  .getServer()
                  .getEventManager()
                  .fireAndForget(new PostRegisterEvent(taskEvent -> {}, null, newPlayer, password));

              player.sendMessage(
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.REGISTER_SUCCESSFUL));
              this.logger.info("Player {} registered successfully.", player.getUsername());

              this.authenticationService.updateLoginData(player);
              this.plugin.getLimboFactory().passLoginLimbo(player);

            } catch (SQLRuntimeException e) {
              this.logger.error("SQL error during registration for {}:", player.getUsername(), e);
              player.sendMessage(
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED));
            } catch (Exception e) {
              this.logger.error(
                  "Unexpected error during registration for {}:", player.getUsername(), e);
              player.sendMessage(
                  currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED));
            }
          } else {
            this.logger.info(
                "Registration for player {} was bypassed by an event handler.",
                player.getUsername());
            this.authenticationService.updateLoginData(player);
            this.plugin.getLimboFactory().passLoginLimbo(player);
          }
        };

    PreRegisterEvent preEvent =
        new PreRegisterEvent(eventConsumer, TaskEvent.Result.NORMAL, player);
    // FIX APPLIED HERE: Removed createExecutor
    this.plugin.getServer().getEventManager().fire(preEvent).thenAcceptAsync(eventConsumer);
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return this.configManager
        .getCommandPermissionState()
        .HELP
        .hasPermission(invocation.source(), "limboauth.commands.register");
  }
}
