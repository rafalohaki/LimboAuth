package net.elytrium.limboauth.command;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import java.util.function.BiFunction;

/**
 * Enum representing the permission state for a command. This determines how command access is
 * checked based on configuration and player permissions.
 */
public enum CommandPermissionState {
  /** Command is always disallowed. */
  FALSE((source, permission) -> false),
  /**
   * Command is allowed if the player does not have the permission set to false (effectively
   * public).
   */
  TRUE((source, permission) -> source.getPermissionValue(permission) != Tristate.FALSE),
  /** Command is allowed only if the player explicitly has the specified permission. */
  PERMISSION(PermissionSubject::hasPermission);

  private final BiFunction<PermissionSubject, String, Boolean> hasPermissionFunction;

  CommandPermissionState(BiFunction<PermissionSubject, String, Boolean> hasPermissionFunction) {
    this.hasPermissionFunction = hasPermissionFunction;
  }

  /**
   * Checks if the given {@link PermissionSubject} has permission according to this state.
   *
   * @param permissionSubject The subject (e.g., player or console) to check.
   * @param permission The permission string to check against if state is {@code PERMISSION}.
   * @return {@code true} if the subject has permission, {@code false} otherwise.
   */
  public boolean hasPermission(PermissionSubject permissionSubject, String permission) {
    return this.hasPermissionFunction.apply(permissionSubject, permission);
  }
}
