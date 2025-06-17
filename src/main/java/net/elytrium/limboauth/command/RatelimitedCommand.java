package net.elytrium.limboauth.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.service.ConfigManager;
import net.kyori.adventure.text.Component;

/**
 * Abstract base class for commands that should be rate-limited. It uses a global {@link
 * LimboAuth#RATELIMITER} for IP-based rate limiting.
 */
public abstract class RatelimitedCommand implements SimpleCommand {

  /** Configuration manager for accessing settings and permissions. */
  protected final ConfigManager configManager; // Accessible by subclasses

  /**
   * Constructs a RatelimitedCommand.
   *
   * @param configManager The configuration manager for accessing settings like rate limit messages.
   */
  /** Default constructor. */
  public RatelimitedCommand(ConfigManager configManager) {
    this.configManager = configManager;
  }

  @Override
  public final void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    // Fetch the message dynamically using the injected ConfigManager each time
    Component ratelimitedMessageText =
        this.configManager
            .getSerializer()
            .deserialize(configManager.getSettings().MAIN.STRINGS.RATELIMITED);

    if (source instanceof Player) {
      // Access the static RATELIMITER from LimboAuth.
      if (!LimboAuth.RATELIMITER.attempt(((Player) source).getRemoteAddress().getAddress())) {
        source.sendMessage(ratelimitedMessageText);
        return;
      }
    }
    // Pass the fetched ratelimitedMessage to the subclass execute method
    this.execute(source, invocation.arguments(), ratelimitedMessageText);
  }

  /**
   * Executes the command logic if not rate-limited. Subclasses must implement this method to define
   * their specific command behavior.
   *
   * @param source The command source.
   * @param args The command arguments.
   * @param ratelimitedMessageComponent The component message for rate limiting, passed for context
   *     or potential use.
   */
  protected abstract void execute(
      CommandSource source, String[] args, Component ratelimitedMessageComponent);
}
