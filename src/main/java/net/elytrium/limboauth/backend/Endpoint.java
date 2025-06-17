package net.elytrium.limboauth.backend;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.service.ConfigManager;

/**
 * Abstract base class for backend API endpoints. Handles common serialization logic for endpoint
 * requests and responses.
 */
public abstract class Endpoint {

  /** The main LimboAuth plugin instance. */
  protected final LimboAuth plugin;

  /** The endpoint type identifier. */
  protected String type;

  /** The username for this endpoint request. */
  protected String
      username; // Uczyniono protected dla łatwiejszego dostępu w podklasach lub listenerze

  /**
   * Constructs an Endpoint.
   *
   * @param plugin The main LimboAuth plugin instance.
   */
  /** Default constructor. */
  public Endpoint(LimboAuth plugin) {
    this.plugin = plugin;
  }

  /**
   * Constructs an Endpoint with a specific type and username.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param type The type identifier of this endpoint.
   * @param username The username this endpoint pertains to.
   */
  /** Default constructor. */
  public Endpoint(LimboAuth plugin, String type, String username) {
    this.plugin = plugin;
    this.type = type;
    this.username = username;
  }

  /**
   * Sets the username for this endpoint. Used by {@link
   * net.elytrium.limboauth.listener.BackendEndpointsListener} after initial parsing of the request.
   *
   * @param username The username.
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Writes the endpoint data to the provided output stream. This includes common header information
   * like type, version, token, and username, followed by endpoint-specific content written by
   * {@link #writeContents(ByteArrayDataOutput)}.
   *
   * @param output The output stream to write to.
   */
  public void write(ByteArrayDataOutput output) {
    ConfigManager configManager = this.plugin.getConfigManager(); // Pobierz ConfigManager
    output.writeUTF(this.type);
    // Użyj configManager do dostępu do settings
    if (!this.type.equals("available_endpoints")
        && !configManager.getSettings().MAIN.BACKEND_API.ENABLED_ENDPOINTS.contains(this.type)) {
      output.writeInt(-1); // Endpoint not enabled
      output.writeUTF(this.username != null ? this.username : ""); // Ensure username is not null
      return;
    }

    output.writeInt(1); // Version of the data structure
    output.writeUTF(configManager.getSettings().MAIN.BACKEND_API.TOKEN);
    output.writeUTF(this.username != null ? this.username : ""); // Ensure username is not null
    this.writeContents(output);
  }

  /**
   * Reads endpoint-specific data from the provided input stream. The common header (type, username)
   * is typically read by the listener before this method is called. This method should be
   * implemented by subclasses if they expect additional data in the request.
   *
   * @param input The input stream to read from.
   */
  public void read(ByteArrayDataInput input) {
    // Wersja i nazwa użytkownika są już odczytane przez BackendEndpointsListener
    // Ta metoda powinna czytać tylko *dodatkowe* dane, jeśli endpoint ich oczekuje.
    // Dla większości obecnych endpointów, ta metoda będzie pusta lub będzie rzucać
    // UnsupportedOperationException,
    // ponieważ wszystkie potrzebne dane (username) są już ustawione.
    // Jeśli jakiś endpoint potrzebuje więcej danych z plugin message, zaimplementuje to tutaj.
    this.readContents(input);
  }

  /**
   * Abstract method for subclasses to write their specific content to the output stream.
   *
   * @param output The output stream to write to.
   */
  public abstract void writeContents(ByteArrayDataOutput output);

  /**
   * Abstract method for subclasses to read their specific content from the input stream.
   *
   * @param input The input stream to read from.
   */
  public abstract void readContents(ByteArrayDataInput input); // Do odczytu dodatkowych danych
}
