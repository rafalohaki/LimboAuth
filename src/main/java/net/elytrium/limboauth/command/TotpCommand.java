package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.slf4j.Logger;

/**
 * Command for managing Two-Factor Authentication (TOTP) for player accounts. Allows enabling TOTP
 * with QR code/secret display and disabling TOTP with a valid code.
 */
public class TotpCommand extends RatelimitedCommand {

  private final Logger logger;
  private final DatabaseService databaseService;
  private final AuthenticationService authenticationService;
  private final LimboAuth plugin;
  // ConfigManager is available via super.configManager

  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final RecoveryCodeGenerator codesGenerator = new RecoveryCodeGenerator();

  /**
   * Constructs the TotpCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param databaseService Service for database interactions.
   * @param authenticationService Service for authentication logic.
   * @param configManager Service for accessing configuration.
   */
  public TotpCommand(
      LimboAuth plugin,
      DatabaseService databaseService,
      AuthenticationService authenticationService,
      ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
    this.databaseService = databaseService;
    this.authenticationService = authenticationService;
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

    final Component usageMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_USAGE);
    final Component notRegisteredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.NOT_REGISTERED);
    final Component wrongPasswordMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.WRONG_PASSWORD);
    final Component alreadyEnabledMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_ALREADY_ENABLED);
    final Component errorOccurredMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.ERROR_OCCURRED);
    final Component successfulEnableMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_SUCCESSFUL);
    final Component qrMsg = currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_QR);
    final String tokenMsgFormat = currentSettings.MAIN.STRINGS.TOTP_TOKEN;
    final String recoveryMsgFormat = currentSettings.MAIN.STRINGS.TOTP_RECOVERY;
    final Component disabledMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_DISABLED);
    final Component wrongCodeMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.TOTP_WRONG);
    final Component crackedCommandMsg =
        currentSerializer.deserialize(currentSettings.MAIN.STRINGS.CRACKED_COMMAND);

    if (args.length == 0) {
      source.sendMessage(usageMsg);
      return;
    }

    String usernameLowercase = commandPlayer.getUsername().toLowerCase(Locale.ROOT);
    RegisteredPlayer registeredPlayer =
        this.databaseService.findPlayerByLowercaseNickname(usernameLowercase);

    if (args[0].equalsIgnoreCase("enable")) {
      boolean needsPasswordCheck = currentSettings.MAIN.TOTP_NEED_PASSWORD;
      if ((needsPasswordCheck && args.length != 2) || (!needsPasswordCheck && args.length != 1)) {
        source.sendMessage(usageMsg);
        return;
      }

      if (registeredPlayer == null) {
        source.sendMessage(notRegisteredMsg);
        return;
      }
      if (registeredPlayer
          .getHash()
          .isEmpty()) { // Premium accounts generally don't use TOTP this way
        source.sendMessage(crackedCommandMsg);
        return;
      }
      if (needsPasswordCheck && !authenticationService.checkPassword(args[1], registeredPlayer)) {
        source.sendMessage(wrongPasswordMsg);
        return;
      }
      if (registeredPlayer.getTotpToken() != null && !registeredPlayer.getTotpToken().isEmpty()) {
        source.sendMessage(alreadyEnabledMsg);
        return;
      }

      String secret = this.secretGenerator.generate();
      try {
        registeredPlayer.setTotpToken(secret);
        this.databaseService.updatePlayer(registeredPlayer);

        source.sendMessage(successfulEnableMsg);
        QrData data =
            new QrData.Builder()
                .label(commandPlayer.getUsername())
                .secret(secret)
                .issuer(currentSettings.MAIN.TOTP_ISSUER)
                .build();
        String qrUrl =
            currentSettings.MAIN.QR_GENERATOR_URL.replace(
                "{data}", URLEncoder.encode(data.getUri(), StandardCharsets.UTF_8));
        source.sendMessage(qrMsg.clickEvent(ClickEvent.openUrl(qrUrl)));

        source.sendMessage(
            currentSerializer
                .deserialize(MessageFormat.format(tokenMsgFormat, secret))
                .clickEvent(ClickEvent.copyToClipboard(secret)));
        String codes =
            String.join(
                ", ",
                this.codesGenerator.generateCodes(currentSettings.MAIN.TOTP_RECOVERY_CODES_AMOUNT));
        source.sendMessage(
            currentSerializer
                .deserialize(MessageFormat.format(recoveryMsgFormat, codes))
                .clickEvent(ClickEvent.copyToClipboard(codes)));

      } catch (SQLRuntimeException e) {
        this.logger.error("SQL error enabling TOTP for {}:", usernameLowercase, e);
        source.sendMessage(errorOccurredMsg);
      } catch (Exception e) {
        this.logger.error("Unexpected error enabling TOTP for {}:", usernameLowercase, e);
        source.sendMessage(errorOccurredMsg);
      }

    } else if (args[0].equalsIgnoreCase("disable")) {
      if (args.length != 2) { // Always needs <key>
        source.sendMessage(usageMsg);
        return;
      }
      if (registeredPlayer == null) {
        source.sendMessage(notRegisteredMsg);
        return;
      }
      if (registeredPlayer.getTotpToken() == null || registeredPlayer.getTotpToken().isEmpty()) {
        source.sendMessage(
            currentSerializer.deserialize(
                currentSettings.MAIN.STRINGS.TOTP_NOT_ENABLED)); // Add this string
        return;
      }

      if (AuthSessionHandler.TOTP_CODE_VERIFIER.isValidCode(
          registeredPlayer.getTotpToken(), args[1])) {
        try {
          registeredPlayer.setTotpToken("");
          this.databaseService.updatePlayer(registeredPlayer);
          source.sendMessage(disabledMsg);
        } catch (SQLRuntimeException e) {
          this.logger.error("SQL error disabling TOTP for {}:", usernameLowercase, e);
          source.sendMessage(errorOccurredMsg);
        }
      } else {
        source.sendMessage(wrongCodeMsg);
      }
    } else {
      source.sendMessage(usageMsg);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return super.configManager
        .getCommandPermissionState()
        .TOTP
        .hasPermission(invocation.source(), "limboauth.commands.totp");
  }
}
