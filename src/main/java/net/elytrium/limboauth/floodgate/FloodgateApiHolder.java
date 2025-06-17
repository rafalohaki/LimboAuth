package net.elytrium.limboauth.floodgate;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A holder class for the Floodgate API instance. This class safely initializes and provides access
 * to the Floodgate API, handling cases where Floodgate might not be present or an error occurs
 * during initialization.
 */
public class FloodgateApiHolder {

  private static final Logger LOGGER = LoggerFactory.getLogger(FloodgateApiHolder.class);
  private final FloodgateApi floodgateApi;

  /**
   * Constructs a FloodgateApiHolder and initializes the Floodgate API instance. Logs information
   * about the initialization status.
   */
  /** Default constructor. */
  public FloodgateApiHolder() {
    FloodgateApi apiInstance = null;
    try {
      apiInstance = FloodgateApi.getInstance();
      if (apiInstance != null) {
        LOGGER.info("FloodgateApi initialized successfully via getInstance().");
      } else {
        LOGGER.warn(
            "FloodgateApi.getInstance() returned null. Floodgate integration may not work.");
      }
    } catch (Exception e) {
      LOGGER.error(
          "Error obtaining FloodgateApi instance. Floodgate integration will be disabled.", e);
    } catch (NoClassDefFoundError e) {
      LOGGER.warn(
          "FloodgateApi class not found. Floodgate integration will be disabled. This is expected if Floodgate is not installed.");
    }
    this.floodgateApi = apiInstance;
  }

  /**
   * Checks if a player with the given UUID is a Floodgate (Bedrock) player.
   *
   * @param uuid The UUID of the player to check.
   * @return {@code true} if the player is a Floodgate player, {@code false} otherwise or if
   *     Floodgate API is unavailable.
   */
  public boolean isFloodgatePlayer(UUID uuid) {
    if (floodgateApi == null) {
      return false;
    }
    try {
      return floodgateApi.isFloodgatePlayer(uuid);
    } catch (Exception e) {
      LOGGER.error("Error checking if player {} is Floodgate player: {}", uuid, e.getMessage());
      return false;
    }
  }

  /**
   * Gets the length of the prefix used for Floodgate player usernames.
   *
   * @return The length of the Floodgate player prefix, or 0 if Floodgate API is unavailable or an
   *     error occurs.
   */
  public int getPrefixLength() {
    if (floodgateApi == null) {
      return 0;
    }
    try {
      return floodgateApi.getPlayerPrefix().length();
    } catch (Exception e) {
      LOGGER.error("Error getting Floodgate prefix length: {}", e.getMessage());
      return 0;
    }
  }

  /**
   * Gets the prefix string used for Floodgate player usernames.
   *
   * @return The Floodgate player prefix string, or an empty string if Floodgate API is unavailable
   *     or an error occurs.
   */
  public String getPlayerPrefix() {
    if (floodgateApi == null) {
      return "";
    }
    try {
      return floodgateApi.getPlayerPrefix();
    } catch (Exception e) {
      LOGGER.error("Error getting Floodgate player prefix: {}", e.getMessage());
      return "";
    }
  }

  /**
   * Gets the raw FloodgateApi instance.
   *
   * @return The {@link FloodgateApi} instance, or {@code null} if it's not available.
   */
  public FloodgateApi getApi() {
    return floodgateApi;
  }
}
