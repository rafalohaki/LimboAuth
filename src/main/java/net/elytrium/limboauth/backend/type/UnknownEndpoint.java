package net.elytrium.limboauth.backend.type;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.backend.Endpoint;

/**
 * Represents an endpoint that is not recognized or supported by the backend API. This class is used
 * to send a specific error code back to the requester indicating an unknown endpoint type.
 */
public class UnknownEndpoint extends Endpoint {

  private String type;

  /**
   * Constructs an UnknownEndpoint without a specific type. This constructor is typically used when
   * the type cannot be determined.
   *
   * @param plugin The main LimboAuth plugin instance.
   */
  /** Default constructor. */
  public UnknownEndpoint(LimboAuth plugin) {
    super(plugin);
  }

  /**
   * Constructs an UnknownEndpoint with a specific type.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param type The type identifier of the unknown endpoint.
   */
  /** Default constructor. */
  public UnknownEndpoint(LimboAuth plugin, String type) {
    super(plugin);
    this.type = type;
  }

  /**
   * Writes a response indicating an unknown endpoint. The response includes the original type
   * requested and an error code (-2).
   *
   * @param output The output stream to write to.
   */
  @Override
  public void write(ByteArrayDataOutput output) {
    output.writeUTF(this.type != null ? this.type : "unknown"); // Ensure type is not null
    output.writeInt(-2); // Error code for unknown endpoint
  }

  /**
   * Reading is not supported for UnknownEndpoint as it's a response-only type.
   *
   * @param input The input stream (unused).
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void read(ByteArrayDataInput input) {
    throw new UnsupportedOperationException("Cannot read data for an UnknownEndpoint.");
  }

  /**
   * Writing specific contents is not supported for UnknownEndpoint. The main {@link
   * #write(ByteArrayDataOutput)} method handles the entire response.
   *
   * @param output The output stream (unused).
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void writeContents(ByteArrayDataOutput output) {
    throw new UnsupportedOperationException(
        "UnknownEndpoint does not have specific contents to write.");
  }

  /**
   * Reading specific contents is not supported for UnknownEndpoint.
   *
   * @param input The input stream (unused).
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void readContents(ByteArrayDataInput input) {
    throw new UnsupportedOperationException(
        "UnknownEndpoint does not have specific contents to read.");
  }

  /**
   * Returns a string representation of the UnknownEndpoint.
   *
   * @return A string representation including the type.
   */
  @Override
  public String toString() {
    return "UnknownEndpoint{" + "type='" + (this.type != null ? this.type : "unknown") + '\'' + '}';
  }
}
