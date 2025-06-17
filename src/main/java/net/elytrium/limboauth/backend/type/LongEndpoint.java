package net.elytrium.limboauth.backend.type;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.backend.Endpoint;

/**
 * An endpoint that represents a long value. This can be used for predefined long values or as a
 * base for database-driven long values.
 */
public class LongEndpoint extends Endpoint {

  /** The long value stored by this endpoint. */
  protected long value; // Zmieniono na protected

  /**
   * Constructs a LongEndpoint with a predefined value.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param type The type identifier of this endpoint.
   * @param username The username this endpoint pertains to.
   * @param value The long value for this endpoint.
   */
  /** Default constructor. */
  public LongEndpoint(LimboAuth plugin, String type, String username, long value) {
    super(plugin, type, username);
    this.value = value;
  }

  /**
   * Writes the long value to the output stream.
   *
   * @param output The output stream to write to.
   */
  @Override
  public void writeContents(ByteArrayDataOutput output) {
    output.writeLong(this.value);
  }

  /**
   * Reads content for the LongEndpoint. For basic LongEndpoints, this is typically empty as the
   * value is set at construction.
   *
   * @param input The input stream to read from.
   */
  @Override
  public void readContents(ByteArrayDataInput input) {
    // Zwykle puste dla LongEndpoint, chyba że dane są przekazywane dodatkowo.
    // Jeśli `function` byłoby używane, to tutaj: this.value = this.function.apply(this.username);
    // Ale w obecnej strukturze, wartość jest ustawiana przez konstruktor lub LongDatabaseEndpoint.
  }

  /**
   * Sets the long value for this endpoint.
   *
   * @param value The new long value.
   */
  protected void setValue(long value) {
    this.value = value;
  }

  /**
   * Returns a string representation of the LongEndpoint.
   *
   * @return A string representation including username and value.
   */
  @Override
  public String toString() {
    return "LongEndpoint{" + "username='" + this.username + '\'' + ", value=" + this.value + '}';
  }
}
