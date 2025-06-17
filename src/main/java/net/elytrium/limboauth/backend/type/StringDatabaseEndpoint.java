package net.elytrium.limboauth.backend.type;

import java.util.function.Function;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.service.DatabaseService;
import org.slf4j.Logger;

/**
 * An endpoint that retrieves a string value from the database for a specific player. The value is
 * determined by the provided dataExtractor function.
 */
public class StringDatabaseEndpoint extends StringEndpoint {
  private final DatabaseService databaseService;
  private final Logger logger;

  /**
   * Constructs a StringDatabaseEndpoint. It fetches player data from the database and applies the
   * dataExtractor to get the string value. If the player is not found or an error occurs, an empty
   * string is set.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param type The type identifier of this endpoint.
   * @param username The username of the player to fetch data for.
   * @param dataExtractor A function to extract the string value from a {@link RegisteredPlayer}
   *     object.
   */
  public StringDatabaseEndpoint(
      LimboAuth plugin,
      String type,
      String username,
      Function<RegisteredPlayer, String> dataExtractor) {
    super(plugin, type, username, ""); // Wywołaj konstruktor klasy bazowej
    this.databaseService = this.plugin.getDatabaseService();
    this.logger = this.plugin.getLogger();

    RegisteredPlayer player =
        this.databaseService.findPlayerByLowercaseNickname(username.toLowerCase());
    if (player == null) {
      this.setValue(""); // Użyj settera z klasy bazowej
      this.logger.debug("StringDatabaseEndpoint: Player {} not found for type {}.", username, type);
    } else {
      try {
        this.setValue(dataExtractor.apply(player)); // Użyj settera z klasy bazowej
      } catch (Exception e) {
        this.logger.error(
            "StringDatabaseEndpoint: Error extracting data for player {} type {}:",
            username,
            type,
            e);
        this.setValue(""); // Użyj settera z klasy bazowej
      }
    }
  }
}
