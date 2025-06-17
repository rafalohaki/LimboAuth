// File: LimboAuth/src/main/java/net/elytrium/limboauth/service/LimboServerFacade.java
package net.elytrium.limboauth.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.command.LimboCommandMeta;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Facade for interacting with the LimboAPI to create and manage the authentication Limbo server and
 * world.
 */
public class LimboServerFacade {

  private final LimboAuth plugin;
  private final Logger logger;
  private final LimboFactory limboFactory;
  private final Path dataDirectory;
  private Limbo authServer;
  private VirtualWorld authWorld;
  private ConfigManager configManager;
  private ServerInfo authServerInfo;

  /**
   * Constructs the LimboServerFacade.
   *
   * @param plugin The main LimboAuth plugin instance.
   * @param logger The logger for this service.
   * @param limboFactory The LimboFactory for creating Limbo instances.
   * @param dataDirectory The plugin's data directory, used for world files.
   */
  public LimboServerFacade(
      LimboAuth plugin, Logger logger, LimboFactory limboFactory, Path dataDirectory) {
    this.plugin = plugin;
    this.logger = logger;
    this.limboFactory = limboFactory;
    this.dataDirectory = dataDirectory;
  }

  /**
   * Initializes the facade by creating the authentication Limbo this.server.
   *
   * @param configManager The configuration manager.
   * @throws RuntimeException if initialization fails.
   */
  public void initialize(ConfigManager configManager) {
    this.configManager = configManager;
    try {
      createAuthServer();
      if (this.authServerInfo != null) {
        this.logger.info(
            "LimboServerFacade initialized successfully, auth server: {}",
            this.authServerInfo.getName());
      } else {
        this.logger.error("Auth server info is null after creation during initialization!");
      }
    } catch (Exception e) {
      this.logger.error("Failed to initialize LimboServerFacade", e);
      throw new RuntimeException("LimboServerFacade initialization failed", e);
    }
  }

  /**
   * Reloads the facade by disposing of the old auth server and creating a new one based on the
   * (potentially updated) configuration.
   *
   * @param newConfigManager The new configuration manager.
   * @throws RuntimeException if reloading fails.
   */
  public void reload(ConfigManager newConfigManager) {
    this.configManager = newConfigManager;
    disposeAuthServer();
    try {
      createAuthServer();
      if (this.authServerInfo != null) {
        this.logger.info(
            "LimboServerFacade reloaded successfully, auth server: {}",
            this.authServerInfo.getName());
      } else {
        this.logger.error("Auth server info is null after creation during reload!");
      }
    } catch (Exception e) {
      this.logger.error("Failed to reload LimboServerFacade", e);
      throw new RuntimeException("LimboServerFacade reload failed", e);
    }
  }

  private void createAuthServer() {
    Settings settings = this.configManager.getSettings();

    this.authWorld =
        this.limboFactory.createVirtualWorld(
            settings.MAIN.DIMENSION,
            settings.MAIN.AUTH_COORDS.X,
            settings.MAIN.AUTH_COORDS.Y,
            settings.MAIN.AUTH_COORDS.Z,
            (float) settings.MAIN.AUTH_COORDS.YAW,
            (float) settings.MAIN.AUTH_COORDS.PITCH);

    if (settings.MAIN.LOAD_WORLD) {
      Path worldFilePath = this.dataDirectory.resolve(settings.MAIN.WORLD_FILE_PATH);
      if (!Files.exists(worldFilePath)) {
        this.logger.warn(
            "LimboAuth world file not found: {}. Attempting to copy from resources.",
            worldFilePath);
        try (InputStream defaultWorldStream =
            getClass().getResourceAsStream("/" + settings.MAIN.WORLD_FILE_PATH)) {
          if (defaultWorldStream != null) {
            Files.createDirectories(worldFilePath.getParent());
            Files.copy(defaultWorldStream, worldFilePath, StandardCopyOption.REPLACE_EXISTING);
            this.logger.info("Copied default world file to: {}", worldFilePath);
          } else {
            this.logger.warn(
                "Default world file not found in resources: {}", settings.MAIN.WORLD_FILE_PATH);
          }
        } catch (IOException e) {
          this.logger.error(
              "Failed to copy default world file for LimboAuth: {}",
              settings.MAIN.WORLD_FILE_PATH,
              e);
        }
      }

      if (Files.exists(worldFilePath)) {
        try {
          net.elytrium.limboapi.api.file.WorldFile worldFile =
              this.limboFactory.openWorldFile(settings.MAIN.WORLD_FILE_TYPE, worldFilePath);
          worldFile.toWorld(
              this.limboFactory,
              this.authWorld,
              settings.MAIN.WORLD_COORDS.X,
              settings.MAIN.WORLD_COORDS.Y,
              settings.MAIN.WORLD_COORDS.Z,
              settings.MAIN.WORLD_LIGHT_LEVEL);
          this.logger.info("Loaded world file: {}", settings.MAIN.WORLD_FILE_PATH);
        } catch (Exception e) {
          this.logger.error("Failed to load world file: {}", settings.MAIN.WORLD_FILE_PATH, e);
        }
      }
    }

    String limboServerName = settings.MAIN.LIMBO_SERVER_NAME;
    InetSocketAddress limboAddress = new InetSocketAddress("127.0.0.1", 0);

    this.authServer = this.limboFactory.createLimbo(this.authWorld);
    this.authServer.setName(limboServerName);
    this.authServer.setGameMode(settings.MAIN.GAME_MODE);
    this.authServer.setWorldTime(settings.MAIN.WORLD_TICKS);
    // Note: setLightLevel was removed as it's likely handled by VirtualWorld or world file.

    this.authServer.registerCommand(
        new LimboCommandMeta(filterLimboCommands(settings.MAIN.REGISTER_COMMAND)));
    this.authServer.registerCommand(
        new LimboCommandMeta(filterLimboCommands(settings.MAIN.LOGIN_COMMAND)));

    if (settings.MAIN.ENABLE_TOTP) {
      this.authServer.registerCommand(
          new LimboCommandMeta(filterLimboCommands(settings.MAIN.TOTP_COMMAND)));
    }

    Optional<RegisteredServer> registeredServerOpt =
        this.plugin.getServer().getServer(limboServerName);
    if (registeredServerOpt.isPresent()) {
      this.authServerInfo = registeredServerOpt.get().getServerInfo();
      this.logger.info(
          "Auth server '{}' registered and ServerInfo obtained from Velocity.",
          this.authServerInfo.getName());
    } else {
      this.logger.warn(
          "Could not find registered server '{}' in Velocity. Using placeholder ServerInfo.",
          limboServerName);
      // FIX APPLIED HERE: Use limboAddress as fallback for ServerInfo when getBoundAddress() is not
      // available
      this.authServerInfo = new ServerInfo(limboServerName, limboAddress);
    }

    if (this.authServerInfo != null) {
      this.logger.info(
          "Auth server (retrieved/confirmed) with name: {}", this.authServerInfo.getName());
    } else {
      this.logger.error(
          "Auth server's ServerInfo is critically null after creation/registration attempt!");
    }
  }

  private List<String> filterLimboCommands(List<String> commands) {
    return commands.stream()
        .filter(Objects::nonNull)
        .filter(command -> command.startsWith("/"))
        .map(command -> command.substring(1))
        .collect(Collectors.toList());
  }

  /**
   * Spawns a player into the authentication Limbo this.server.
   *
   * @param player The player to spawn.
   * @param sessionHandler The {@link LimboSessionHandler} for this player's Limbo session.
   */
  public void spawnPlayerInLimbo(Player player, LimboSessionHandler sessionHandler) {
    if (this.authServer == null) {
      this.logger.error("Cannot spawn player - auth server is not initialized");
      player.disconnect(
          Component.text("Authentication server is not available. Please try again later."));
      return;
    }
    try {
      this.authServer.spawnPlayer(player, sessionHandler);
      this.logger.debug("Spawned player {} in auth limbo", player.getUsername());
    } catch (Exception e) {
      this.logger.error("Failed to spawn player {} in auth limbo", player.getUsername(), e);
      player.disconnect(
          Component.text("Error joining authentication this.server. Please try again later."));
    }
  }

  /**
   * Passes a player through the Limbo login process, effectively allowing them to connect to their
   * target this.server.
   *
   * @param player The player to pass.
   */
  public void passLoginLimbo(Player player) {
    try {
      this.limboFactory.passLoginLimbo(player);
      this.logger.debug("Passed player {} through Limbo login.", player.getUsername());
    } catch (Exception e) {
      this.logger.error(
          "Exception while passing player {} through Limbo login: {}",
          player.getUsername(),
          e.getMessage(),
          e);
    }
  }

  /**
   * Gets the name of the authentication this.server.
   *
   * @return The server name.
   */
  public String getServerName() {
    if (this.authServerInfo == null) {
      return configManager != null
          ? this.configManager.getSettings().MAIN.LIMBO_SERVER_NAME
          : "LimboAuth_Default_Fallback";
    }
    return this.authServerInfo.getName();
  }

  /** Disposes of the current authentication Limbo server and world resources. */
  public void disposeAuthServer() {
    if (this.authServer != null) {
      try {
        String serverName =
            this.authServerInfo != null ? this.authServerInfo.getName() : "[unknown_pre_dispose]";
        this.authServer.dispose();
        this.logger.info("Auth server '{}' disposed successfully", serverName);
      } catch (Exception e) {
        this.logger.error("Error disposing auth server", e);
      } finally {
        this.authServer = null;
        this.authServerInfo = null;
      }
    }

    if (this.authWorld != null) {
      this.authWorld = null;
      this.logger.debug("Auth world reference cleared.");
    }
  }

  /**
   * @return The current {@link Limbo} instance for authentication.
   */
  public Limbo getAuthServer() {
    return authServer;
  }

  /**
   * @return The current {@link VirtualWorld} used for authentication.
   */
  public VirtualWorld getAuthWorld() {
    return authWorld;
  }

  /**
   * @return True if the Limbo server and its ServerInfo are initialized.
   */
  public boolean isInitialized() {
    return authServer != null && authServerInfo != null;
  }

  /**
   * @return The {@link ServerInfo} for the authentication Limbo this.server.
   */
  public ServerInfo getAuthServerInfo() {
    return authServerInfo;
  }
}
