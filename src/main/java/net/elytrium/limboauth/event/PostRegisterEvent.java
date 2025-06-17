package net.elytrium.limboauth.event;

import java.util.function.Consumer;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.model.RegisteredPlayer;

/**
 * Event fired after a player has successfully completed the registration process and their data has
 * been saved to the database.
 *
 * <p>This is an asynchronous event. Handlers can perform actions that might take time, such as
 * database lookups or external API calls. The main authentication flow will wait for this event to
 * complete (or be cancelled) before proceeding. The player is typically still in the Limbo world
 * when this event is fired.
 */
public class PostRegisterEvent extends PostEvent {

  /**
   * Constructs a PostRegisterEvent.
   *
   * @param onComplete A consumer to be called when this event's processing is finished or
   *     cancelled.
   * @param player The {@link LimboPlayer} who has been registered.
   * @param playerInfo The {@link RegisteredPlayer} data for the newly registered player.
   * @param password The password used for registration (handle with care).
   */
  public PostRegisterEvent(
      Consumer<TaskEvent> onComplete,
      LimboPlayer player,
      RegisteredPlayer playerInfo,
      String password) {
    super(onComplete, player, playerInfo, password);
  }
}
