package net.elytrium.limboauth.event;

import com.velocitypowered.api.proxy.Player;
import java.util.function.Consumer;

/**
 * Event fired before a new player attempts to register. This event occurs after initial checks
 * (like nickname validation) but before the player is prompted for a password or their data is
 * saved to the database.
 *
 * <p>This is an asynchronous event. Handlers can perform actions that might take time, such as
 * checking against an external whitelist or performing additional validation. The main registration
 * flow will wait for this event to complete (or be cancelled) before proceeding. Handlers can
 * cancel this event to prevent the player from attempting to register, or modify its result to
 * bypass standard registration procedures.
 */
public class PreRegisterEvent extends PreEvent {

  /**
   * Constructs a PreRegisterEvent.
   *
   * @param onComplete A consumer to be called when this event's processing is finished or
   *     cancelled.
   * @param result The initial {@link Result} of this event.
   * @param player The {@link Player} attempting to register.
   */
  /** Default constructor. */
  public PreRegisterEvent(Consumer<TaskEvent> onComplete, Result result, Player player) {
    super(onComplete, result, player);
  }
}
