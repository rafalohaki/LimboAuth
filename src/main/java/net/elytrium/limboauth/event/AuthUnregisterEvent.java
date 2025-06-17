package net.elytrium.limboauth.event;

/**
 * Event fired when a player is unregistered from the LimboAuth system. This event occurs before the
 * player's data is actually removed from the database. It can be used to perform actions related to
 * a player's unregistration, such as cleaning up data in other systems or logging the event.
 */
public class AuthUnregisterEvent {

  private final String nickname;

  /**
   * Constructs an AuthUnregisterEvent.
   *
   * @param nickname The nickname of the player being unregistered.
   */
  /** Default constructor. */
  public AuthUnregisterEvent(String nickname) {
    this.nickname = nickname;
  }

  /**
   * Gets the nickname of the player being unregistered.
   *
   * @return The player's nickname.
   */
  public String getNickname() {
    return this.nickname;
  }
}
