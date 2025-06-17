package net.elytrium.limboauth.event;

import net.elytrium.limboauth.model.RegisteredPlayer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Event fired when a player's password has been changed. This event provides information about the
 * player, their old password (if provided by the user, e.g. not a force-change), the old password
 * hash, the new password (plaintext, for logging/auditing if necessary), and the new password hash.
 *
 * <p>Caution: The new password is provided in plaintext. Handle this event and its data with care,
 * especially regarding logging or storing the plaintext password.
 */
public class ChangePasswordEvent {

  private final RegisteredPlayer playerInfo;
  @Nullable private final String oldPassword;
  private final String oldHash;
  private final String newPassword;
  private final String newHash;

  /**
   * Constructs a ChangePasswordEvent.
   *
   * @param playerInfo The {@link RegisteredPlayer} information for whom the password was changed.
   * @param oldPassword The old password in plaintext, if it was provided during the change process
   *     (e.g., not a forced change). May be {@code null}.
   * @param oldHash The old hashed password.
   * @param newPassword The new password in plaintext. Handle with care.
   * @param newHash The new hashed password.
   */
  public ChangePasswordEvent(
      RegisteredPlayer playerInfo,
      @Nullable String oldPassword,
      String oldHash,
      String newPassword,
      String newHash) {
    this.playerInfo = playerInfo;
    this.oldPassword = oldPassword;
    this.oldHash = oldHash;
    this.newPassword = newPassword;
    this.newHash = newHash;
  }

  /**
   * Gets the {@link RegisteredPlayer} information for the player whose password was changed.
   *
   * @return The player's registered information.
   */
  public RegisteredPlayer getPlayerInfo() {
    return this.playerInfo;
  }

  /**
   * Gets the old password in plaintext, if it was provided by the user during the change. This will
   * be {@code null} if the password change was forced by an administrator or if the old password
   * was not required/checked.
   *
   * @return The old plaintext password, or {@code null}.
   */
  @Nullable public String getOldPassword() {
    return this.oldPassword;
  }

  /**
   * Gets the old hashed password.
   *
   * @return The old password hash.
   */
  public String getOldHash() {
    return this.oldHash;
  }

  /**
   * Gets the new password in plaintext.
   *
   * <p><strong>Caution:</strong> Handle this with care. Avoid logging or storing plaintext
   * passwords.
   *
   * @return The new plaintext password.
   */
  public String getNewPassword() {
    return this.newPassword;
  }

  /**
   * Gets the new hashed password.
   *
   * @return The new password hash.
   */
  public String getNewHash() {
    return this.newHash;
  }
}
