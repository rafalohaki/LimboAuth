package net.elytrium.limboauth.event;

import java.util.function.Consumer;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.model.RegisteredPlayer;

/**
 * Abstract base class for "post" authentication events, which are fired after a primary
 * authentication action (like registration or login) has occurred and potentially after the
 * player's data has been persisted or updated.
 *
 * <p>These events are typically asynchronous and allow for further actions or modifications before
 * the player is fully passed through to the main server network.
 */
public abstract class PostEvent extends TaskEvent {

  private final LimboPlayer player;
  private final RegisteredPlayer playerInfo;
  private final String password;

  /**
   * Constructs a PostEvent.
   *
   * @param onComplete A consumer called when this event's processing is done or cancelled.
   * @param player The {@link LimboPlayer} involved in the event.
   * @param playerInfo The {@link RegisteredPlayer} data associated with the player.
   * @param password The password used in the preceding action (handle with care).
   */
  protected PostEvent(
      Consumer<TaskEvent> onComplete,
      LimboPlayer player,
      RegisteredPlayer playerInfo,
      String password) {
    super(onComplete);

    this.player = player;
    this.playerInfo = playerInfo;
    this.password = password;
  }

  /**
   * Constructs a PostEvent with an initial result.
   *
   * @param onComplete A consumer called when this event's processing is done or cancelled.
   * @param result The initial {@link Result} of this event.
   * @param player The {@link LimboPlayer} involved in the event.
   * @param playerInfo The {@link RegisteredPlayer} data associated with the player.
   * @param password The password used in the preceding action (handle with care).
   */
  protected PostEvent(
      Consumer<TaskEvent> onComplete,
      Result result,
      LimboPlayer player,
      RegisteredPlayer playerInfo,
      String password) {
    super(onComplete, result);

    this.player = player;
    this.playerInfo = playerInfo;
    this.password = password;
  }

  /**
   * Gets the {@link LimboPlayer} associated with this event.
   *
   * @return The LimboPlayer.
   */
  public LimboPlayer getPlayer() {
    return this.player;
  }

  /**
   * Gets the {@link RegisteredPlayer} data for the player involved in this event.
   *
   * @return The registered player information.
   */
  public RegisteredPlayer getPlayerInfo() {
    return this.playerInfo;
  }

  /**
   * Gets the password that was used in the authentication action preceding this event.
   *
   * <p><strong>Caution:</strong> This is a plaintext password. Handle with extreme care and avoid
   * logging or storing it unnecessarily.
   *
   * @return The plaintext password, or an empty string/null if not applicable.
   */
  public String getPassword() {
    return this.password;
  }
}
