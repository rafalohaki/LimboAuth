package net.elytrium.limboauth.backend.type;

import java.util.function.Function;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.service.DatabaseService;
import org.slf4j.Logger;

/**
 * An endpoint that retrieves a long value from the database for a specific player. The value is
 * determined by the provided dataExtractor function.
 */
public class LongDatabaseEndpoint extends LongEndpoint {
  private final DatabaseService databaseService;
  private final Logger logger;

  /**
   * Constructs a LongDatabaseEndpoint. It fetches player data from the database and applies the
   * dataExtractor to get the long value. If the player is not found or an error occurs, {@link
   * Long#MIN_VALUE} is set.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param type The type identifier of this endpoint.
   * @param username The username of the player to fetch data for.
   * @param dataExtractor A function to extract the long value from a {@link RegisteredPlayer}
   *     object.
   */
  public LongDatabaseEndpoint(
      LimboAuth plugin,
      String type,
      String username,
      Function<RegisteredPlayer, Long> dataExtractor) {
    super(plugin, type, username, 0L); // Wywołaj konstruktor klasy bazowej
    this.databaseService = this.plugin.getDatabaseService();
    this.logger = this.plugin.getLogger();

    RegisteredPlayer player =
        this.databaseService.findPlayerByLowercaseNickname(username.toLowerCase());
    if (player == null) {
      this.setValue(Long.MIN_VALUE); // Użyj settera z klasy bazowej
      this.logger.debug("LongDatabaseEndpoint: Player {} not found for type {}.", username, type);
    } else {
      try {
        this.setValue(dataExtractor.apply(player)); // Użyj settera z klasy bazowej
      } catch (Exception e) {
        this.logger.error(
            "LongDatabaseEndpoint: Error extracting data for player {} type {}:",
            username,
            type,
            e);
        this.setValue(Long.MIN_VALUE); // Użyj settera z klasy bazowej
      }
    }
  }
}
