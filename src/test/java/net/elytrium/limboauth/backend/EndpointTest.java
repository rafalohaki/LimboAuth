package net.elytrium.limboauth.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.lang.reflect.Field;
import net.elytrium.limboauth.backend.type.StringEndpoint;
import org.junit.jupiter.api.Test;

public class EndpointTest {

  @Test
  public void stringEndpointSerialization() throws Exception {
    String username = "testuser";
    String value = "hello";

    StringEndpoint source = new StringEndpoint(null, "premium_state", username, value);
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeInt(0);
    out.writeUTF(username);
    source.writeContents(out);

    ByteArrayDataInput in = ByteStreams.newDataInput(out.toByteArray());
    StringEndpoint target = new StringEndpoint(null, "premium_state", u -> value);
    target.read(in);

    assertEquals(username, target.username);
    Field valueField = StringEndpoint.class.getDeclaredField("value");
    valueField.setAccessible(true);
    assertEquals(value, valueField.get(target));
  }
}
