package net.elytrium.limboauth.event;

import com.velocitypowered.api.proxy.Player;
import java.util.function.Consumer;

/**
 * Abstract base class for "pre" authentication events, which are fired before a primary
 * authentication action (like registration or login) is fully processed.
 *
 * <p>These events are typically asynchronous and allow for cancellation or modification of the
 * authentication flow before it proceeds to more intensive checks or database operations.
 */
public abstract class PreEvent extends TaskEvent {

  private final Player player;

  /**
   * Constructs a PreEvent.
   *
   * @param onComplete A consumer called when this event's processing is done or cancelled.
   * @param result The initial {@link Result} of this event.
   * @param player The {@link Player} involved in the event.
   */
  protected PreEvent(Consumer<TaskEvent> onComplete, Result result, Player player) {
    super(onComplete, result);

    this.player = player;
  }

  /**
   * Gets the {@link Player} associated with this event.
   *
   * @return The Velocity Player.
   */
  public Player getPlayer() {
    return this.player;
  }
}
