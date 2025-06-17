// File: LimboAuth/src/main/java/net/elytrium/limboauth/listener/BackendEndpointsListener.java
package net.elytrium.limboauth.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.backend.Endpoint;
import net.elytrium.limboauth.backend.type.LongDatabaseEndpoint;
import net.elytrium.limboauth.backend.type.StringDatabaseEndpoint;
import net.elytrium.limboauth.backend.type.StringEndpoint;
import net.elytrium.limboauth.backend.type.UnknownEndpoint;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.slf4j.Logger;

/**
 * Listens for LimboAuth backend API plugin messages and dispatches to the configured endpoint
 * factories.
 */
public class BackendEndpointsListener {

  /** Channel identifier for backend API communication. */
  public static final ChannelIdentifier API_CHANNEL =
      MinecraftChannelIdentifier.create("limboauth", "api"); // Changed from backend_api to api

  private static final Map<String, Function<LimboAuth, Function<String, Endpoint>>>
      ENDPOINT_FACTORIES;

  static {
    Map<String, Function<LimboAuth, Function<String, Endpoint>>> factories = new HashMap<>();

    factories.put(
        "available_endpoints",
        plugin -> // 'plugin' to instancja LimboAuth
        (usernameForEndpoint) ->
                new StringEndpoint(
                    plugin, // Użycie poprawnego parametru 'plugin'
                    "available_endpoints",
                    usernameForEndpoint,
                    // Corrected: Use the 'plugin' parameter from the lambda
                    String.join(",", this.plugin.getConfigManager().getEnabledBackendEndpoints())));
    factories.put(
        "premium_state",
        plugin -> // 'plugin' to instancja LimboAuth
        (usernameForEndpoint) ->
                new StringEndpoint(
                    plugin, // Użycie poprawnego parametru 'plugin'
                    "premium_state",
                    usernameForEndpoint,
                    plugin // Użycie poprawnego parametru 'plugin'
                        .getAuthenticationService()
                        .isPremiumInternal(usernameForEndpoint)
                        .getState()
                        .name()));
    factories.put(
        "hash",
        plugin ->
            (usernameForEndpoint) ->
                new StringDatabaseEndpoint(
                    plugin, "hash", usernameForEndpoint, RegisteredPlayer::getHash));
    factories.put(
        "totp_token",
        plugin ->
            (usernameForEndpoint) ->
                new StringDatabaseEndpoint(
                    plugin, "totp_token", usernameForEndpoint, RegisteredPlayer::getTotpToken));
    factories.put(
        "reg_date",
        plugin ->
            (usernameForEndpoint) ->
                new LongDatabaseEndpoint(
                    plugin, "reg_date", usernameForEndpoint, RegisteredPlayer::getRegDate));
    factories.put(
        "login_date",
        plugin ->
            (usernameForEndpoint) ->
                new LongDatabaseEndpoint(
                    plugin, "login_date", usernameForEndpoint, RegisteredPlayer::getLoginDate));
    factories.put(
        "token_issued_at",
        plugin ->
            (usernameForEndpoint) ->
                new LongDatabaseEndpoint(
                    plugin,
                    "token_issued_at",
                    usernameForEndpoint,
                    RegisteredPlayer::getTokenIssuedAt));
    factories.put(
        "uuid",
        plugin ->
            (usernameForEndpoint) ->
                new StringDatabaseEndpoint(
                    plugin, "uuid", usernameForEndpoint, RegisteredPlayer::getUuid));
    factories.put(
        "premium_uuid",
        plugin ->
            (usernameForEndpoint) ->
                new StringDatabaseEndpoint(
                    plugin, "premium_uuid", usernameForEndpoint, RegisteredPlayer::getPremiumUuid));
    factories.put(
        "ip",
        plugin ->
            (usernameForEndpoint) ->
                new StringDatabaseEndpoint(
                    plugin, "ip", usernameForEndpoint, RegisteredPlayer::getIP));
    factories.put(
        "login_ip",
        plugin ->
            (usernameForEndpoint) ->
                new StringDatabaseEndpoint(
                    plugin, "login_ip", usernameForEndpoint, RegisteredPlayer::getLoginIp));

    ENDPOINT_FACTORIES = Collections.unmodifiableMap(factories);
  }

  private final LimboAuth plugin;
  private final Logger logger;

  /**
   * Constructs the BackendEndpointsListener.
   *
   * @param plugin The main LimboAuth plugin instance.
   */
  /** Default constructor. */
  public BackendEndpointsListener(LimboAuth plugin) {
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
  }

  /**
   * Handles incoming plugin messages on the LimboAuth backend API channel. Parses the request,
   * authenticates it using a token, and dispatches to the appropriate {@link Endpoint} factory
   * based on the requested type. The response is then sent back to the requester.
   *
   * @param event The PluginMessageEvent.
   */
  @Subscribe
  @SuppressWarnings("UnstableApiUsage") // For ByteStreams
  public void onPluginMessage(PluginMessageEvent event) {
    if (!API_CHANNEL.equals(event.getIdentifier())) {
      return;
    }

    if (!(event.getSource() instanceof ServerConnection)) {
      this.logger.warn(
          "Received backend API message from non-server source: {}", event.getSource());
      return;
    }
    ServerConnection serverConnection = (ServerConnection) event.getSource();

    event.setResult(PluginMessageEvent.ForwardResult.handled());

    ByteArrayDataInput input = ByteStreams.newDataInput(event.getData());
    ByteArrayDataOutput output = ByteStreams.newDataOutput();

    try {
      String requestType = input.readUTF();
      int version = input.readInt();
      String token = input.readUTF();
      String username = input.readUTF();

      if (version != 1) {
        this.logger.warn(
            "Received backend API request with unsupported version {} from {}",
            version,
            serverConnection.getServerInfo().getName());
        new UnknownEndpoint(plugin, requestType).write(output);
      } else if (!plugin.getConfigManager().getSettings().MAIN.BACKEND_API.TOKEN.equals(token)) {
        this.logger.warn(
            "Received backend API request with invalid token from {}",
            serverConnection.getServerInfo().getName());
        output.writeUTF(requestType);
        output.writeInt(-3); // Invalid token error code
      } else {
        this.logger.debug(
            "Received valid backend API request for type '{}', user '{}' from {}",
            requestType,
            username,
            serverConnection.getServerInfo().getName());
        Function<LimboAuth, Function<String, Endpoint>> endpointCreatorFactory =
            ENDPOINT_FACTORIES.get(requestType);
        Endpoint endpoint;
        if (endpointCreatorFactory != null) {
          Function<String, Endpoint> endpointCreator = endpointCreatorFactory.apply(plugin);
          endpoint = endpointCreator.apply(username);
          // No need to call endpoint.readContents(input) if all data is passed via constructor or
          // setUsername
        } else {
          this.logger.warn("Unknown backend API endpoint type requested: {}", requestType);
          endpoint = new UnknownEndpoint(plugin, requestType);
        }
        endpoint.write(output);
      }
    } catch (Exception e) {
      this.logger.error(
          "Error processing backend API plugin message from {}",
          serverConnection.getServerInfo().getName(),
          e);
      output.writeUTF("error_processing");
      output.writeInt(-99);
    }

    // Send the response back using Velocity's public API
    serverConnection.sendPluginMessage(API_CHANNEL, output.toByteArray());
    this.logger.debug(
        "Sent backend API response to {}", serverConnection.getServerInfo().getName());
  }
}
