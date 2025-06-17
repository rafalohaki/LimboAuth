package net.elytrium.limboauth.event;

import java.util.function.Consumer;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.model.RegisteredPlayer;

/**
 * Event fired after a player has successfully completed the authorization (login) process. This
 * event occurs after password checks and any other login-specific validations (like 2FA if
 * applicable) have passed.
 *
 * <p>This is an asynchronous event. Handlers can perform actions that might take time, such as
 * database lookups or external API calls. The main authentication flow will wait for this event to
 * complete (or be cancelled) before proceeding. The player is typically still in the Limbo world
 * when this event is fired.
 */
public class PostAuthorizationEvent extends PostEvent {

  /**
   * Constructs a PostAuthorizationEvent.
   *
   * @param onComplete A consumer to be called when this event's processing is finished or
   *     cancelled.
   * @param player The {@link LimboPlayer} who has been authorized.
   * @param playerInfo The {@link RegisteredPlayer} data for the authorized player.
   * @param password The password used for authorization (if applicable, handle with care).
   */
  public PostAuthorizationEvent(
      Consumer<TaskEvent> onComplete,
      LimboPlayer player,
      RegisteredPlayer playerInfo,
      String password) {
    super(onComplete, player, playerInfo, password);
  }
}
