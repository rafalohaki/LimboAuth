package net.elytrium.limboauth.event;

import com.velocitypowered.api.proxy.Player;
import java.util.function.Consumer;
import net.elytrium.limboauth.model.RegisteredPlayer;

/**
 * Event fired before a player attempts to authorize (log in). This event occurs after the system
 * has identified that the player is registered but before any password or 2FA checks are performed.
 *
 * <p>This is an asynchronous event. Handlers can perform actions that might take time, such as
 * database lookups or external API calls. The main authentication flow will wait for this event to
 * complete (or be cancelled) before proceeding with login checks. Handlers can cancel this event to
 * prevent the player from attempting to log in, or modify its result to bypass standard login
 * procedures.
 */
public class PreAuthorizationEvent extends PreEvent {

  private final RegisteredPlayer playerInfo;

  /**
   * Constructs a PreAuthorizationEvent.
   *
   * @param onComplete A consumer to be called when this event's processing is finished or
   *     cancelled.
   * @param result The initial {@link Result} of this event.
   * @param player The {@link Player} attempting to authorize.
   * @param playerInfo The {@link RegisteredPlayer} data for the player.
   */
  public PreAuthorizationEvent(
      Consumer<TaskEvent> onComplete, Result result, Player player, RegisteredPlayer playerInfo) {
    super(onComplete, result, player);

    this.playerInfo = playerInfo;
  }

  /**
   * Gets the {@link RegisteredPlayer} data for the player attempting to authorize.
   *
   * @return The registered player information.
   */
  public RegisteredPlayer getPlayerInfo() {
    return this.playerInfo;
  }
}
