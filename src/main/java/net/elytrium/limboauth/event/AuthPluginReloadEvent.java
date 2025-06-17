package net.elytrium.limboauth.event;

/**
 * Event fired when the LimboAuth plugin is reloaded. This event allows other plugins to react to
 * configuration changes and update their internal state accordingly.
 */
public class AuthPluginReloadEvent {

  /** Default constructor for AuthPluginReloadEvent. Creates a new instance of the reload event. */
  public AuthPluginReloadEvent() {
    // Default constructor
  }
}
