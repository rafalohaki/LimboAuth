package net.elytrium.limboauth.backend.type;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.backend.Endpoint;

/**
 * Represents an endpoint that handles a string value. This class can be used directly for
 * predefined string values or as a base for endpoints that fetch string data from other sources
 * (e.g., database).
 */
public class StringEndpoint extends Endpoint {

  protected String value; // Zmieniono na protected

  // Usunięto nieużywane pole 'function'

  /**
   * Constructs a StringEndpoint with a predefined value.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param type The type identifier of this endpoint.
   * @param username The username this endpoint pertains to.
   * @param value The string value for this endpoint.
   */
  /** Default constructor. */
  public StringEndpoint(LimboAuth plugin, String type, String username, String value) {
    super(plugin, type, username);
    this.value = value;
  }

  /**
   * Writes the string value to the output stream. Ensures that a null value is written as an empty
   * string.
   *
   * @param output The output stream to write to.
   */
  @Override
  public void writeContents(ByteArrayDataOutput output) {
    output.writeUTF(this.value != null ? this.value : ""); // Upewnij się, że nie jest null
  }

  /**
   * Reads content for the StringEndpoint. For basic StringEndpoints, this is typically empty as the
   * value is set at construction or by a subclass like {@link StringDatabaseEndpoint}.
   *
   * @param input The input stream to read from.
   */
  @Override
  public void readContents(ByteArrayDataInput input) {
    // Podobnie jak w LongEndpoint, wartość jest ustawiana przez konstruktor lub
    // StringDatabaseEndpoint.
  }

  /**
   * Sets the string value for this endpoint.
   *
   * @param value The new string value.
   */
  protected void setValue(String value) {
    this.value = value;
  }

  /**
   * Returns a string representation of the StringEndpoint.
   *
   * @return A string representation including username and value.
   */
  @Override
  public String toString() {
    return "StringEndpoint{" + "username='" + this.username + '\'' + ", value=" + this.value + '}';
  }
}
