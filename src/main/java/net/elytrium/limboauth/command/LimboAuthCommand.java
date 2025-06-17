package net.elytrium.limboauth.command;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.service.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements the base /limboauth command for administrative actions. This command handles
 * subcommands like "reload" and provides help information.
 */
public class LimboAuthCommand extends RatelimitedCommand {

  private static final List<Component> HELP_MESSAGE =
      List.of(
          Component.text("This server is using LimboAuth and LimboAPI.", NamedTextColor.YELLOW),
          Component.text("(C) 2021 - 2025 Elytrium", NamedTextColor.YELLOW),
          Component.text("https://elytrium.net/github/", NamedTextColor.GREEN),
          Component.empty());

  private static final Component AVAILABLE_SUBCOMMANDS_MESSAGE =
      Component.text("Available subcommands:", NamedTextColor.WHITE);
  private static final Component NO_AVAILABLE_SUBCOMMANDS_MESSAGE =
      Component.text("There is no available subcommands for you.", NamedTextColor.WHITE);

  private final LimboAuth plugin;

  /**
   * Constructs the LimboAuthCommand.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param configManager Service for accessing configuration, used for permissions.
   */
  /** Default constructor. */
  public LimboAuthCommand(LimboAuth plugin, ConfigManager configManager) {
    super(configManager);
    this.plugin = plugin;
  }

  /**
   * Suggests subcommand names for tab completion. Filters suggestions based on the user's
   * permissions for each subcommand.
   *
   * @param invocation The command invocation context.
   * @return A list of suggested subcommand strings.
   */
  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0 || args.length == 1) {
      String currentArg = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
      return Arrays.stream(Subcommand.values())
          .filter(sub -> sub.hasPermission(source, this.configManager))
          .map(Subcommand::getCommand)
          .filter(cmdName -> cmdName.startsWith(currentArg))
          .toList();
    }
    return ImmutableList.of();
  }

  /**
   * Executes the /limboauth command or its subcommands. If no arguments are provided, or if an
   * unknown subcommand is given, it displays help information.
   *
   * @param source The source of the command.
   * @param args The arguments provided with the command.
   * @param ratelimitedMessageComponent The component to display if rate-limited (unused directly
   *     here).
   */
  @Override
  protected void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent) {
    if (args.length == 0) {
      this.showHelp(source);
      return;
    }

    String subCommandStr = args[0].toUpperCase(Locale.ROOT);
    try {
      Subcommand subcommand = Subcommand.valueOf(subCommandStr);
      if (!subcommand.hasPermission(source, this.configManager)) {
        this.showHelp(source);
        return;
      }
      subcommand.executor.execute(this.plugin, source, Arrays.copyOfRange(args, 1, args.length));
    } catch (IllegalArgumentException e) {
      this.showHelp(source); // Unknown subcommand
    }
  }

  /**
   * Shows the help and available subcommands to the sender.
   *
   * @param source The command source to send help to.
   */
  private void showHelp(CommandSource source) {
    HELP_MESSAGE.forEach(source::sendMessage);
    List<Subcommand> available =
        Arrays.stream(Subcommand.values())
            .filter(cmd -> cmd.hasPermission(source, this.configManager))
            .toList();
    if (!available.isEmpty()) {
      source.sendMessage(AVAILABLE_SUBCOMMANDS_MESSAGE);
      for (Subcommand cmd : available) {
        source.sendMessage(Component.text(cmd.getCommand(), NamedTextColor.AQUA));
      }
    } else {
      source.sendMessage(NO_AVAILABLE_SUBCOMMANDS_MESSAGE);
    }
  }

  /**
   * Checks if the command source has permission to execute the base /limboauth command. Permission
   * is granted if the source has the "help" permission or permission for any of the defined
   * subcommands.
   *
   * @param invocation The command invocation context.
   * @return {@code true} if the source has permission, {@code false} otherwise.
   */
  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return this.configManager
            .getCommandPermissionState()
            .HELP
            .hasPermission(invocation.source(), "limboauth.commands.help")
        || Arrays.stream(Subcommand.values())
            .anyMatch(sc -> sc.hasPermission(invocation.source(), this.configManager));
  }

  /**
   * Enum defining the available subcommands for /limboauth. Each subcommand has an associated
   * executor and permission check logic.
   */
  private enum Subcommand {
    RELOAD(
        "reload",
        "limboauth.admin.reload",
        (pluginInstance, source, subArgs) -> {
          if (pluginInstance
              .getConfigManager()
              .getCommandPermissionState()
              .RELOAD
              .hasPermission(source, "limboauth.admin.reload")) {
            pluginInstance.reloadPlugin();
            source.sendMessage(
                pluginInstance
                    .getConfigManager()
                    .getSerializer()
                    .deserialize(
                        pluginInstance.getConfigManager().getSettings().MAIN.STRINGS.RELOAD));
          } else {
            source.sendMessage(
                Component.text(
                    "You do not have permission to use this subcommand.", NamedTextColor.RED));
          }
        });

    private final String command;
    private final String permissionNode;
    private final SubcommandExecutor executor;

    /**
     * Constructs a Subcommand.
     *
     * @param command The command string (e.g., "reload").
     * @param permissionNode The permission node required for this subcommand.
     * @param executor The logic to execute for this subcommand.
     */
    Subcommand(String command, String permissionNode, SubcommandExecutor executor) {
      this.command = command;
      this.permissionNode = permissionNode;
      this.executor = executor;
    }

    /**
     * Gets the command string.
     *
     * @return The command string.
     */
    public String getCommand() {
      return this.command;
    }

    /**
     * Checks if the source has permission for this subcommand based on its configured permission
     * state.
     *
     * @param source The command source.
     * @param cm The ConfigManager to get the permission state.
     * @return True if permitted, false otherwise.
     */
    public boolean hasPermission(CommandSource source, ConfigManager cm) {
      Settings.MAIN.COMMAND_PERMISSION_STATE states =
          cm.getSettings().MAIN.COMMAND_PERMISSION_STATE;
      CommandPermissionState specificState;
      switch (this.command.toLowerCase(Locale.ROOT)) {
        case "reload":
          specificState = states.RELOAD;
          break;
        default:
          return source.hasPermission(this.permissionNode);
      }
      return specificState.hasPermission(source, this.permissionNode);
    }
  }

  /** Functional interface for executing a subcommand. */
  @FunctionalInterface
  private interface SubcommandExecutor {
    /**
     * Executes the subcommand logic.
     *
     * @param plugin The main LimboAuth plugin instance.
     * @param source The command source.
     * @param args The arguments for the subcommand.
     */
    void execute(LimboAuth plugin, CommandSource source, String[] args);
  }
}
