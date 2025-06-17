package net.elytrium.limboauth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiter;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiters;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboauth.event.AuthPluginReloadEvent;
import net.elytrium.limboauth.floodgate.FloodgateApiHolder;
import net.elytrium.limboauth.listener.AuthListener;
import net.elytrium.limboauth.listener.BackendEndpointsListener;
import net.elytrium.limboauth.service.AuthenticationService;
import net.elytrium.limboauth.service.CacheManager;
import net.elytrium.limboauth.service.CommandRegistry;
import net.elytrium.limboauth.service.ConfigManager;
import net.elytrium.limboauth.service.DatabaseService;
import net.elytrium.limboauth.service.LimboServerFacade;
import net.elytrium.limboauth.service.PlayerSessionService;
import net.elytrium.limboauth.service.TaskSchedulingService;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

/**
 * Main plugin class for LimboAuth. Handles plugin initialization, shutdown, and provides access to
 * core services.
 */
@Plugin(
    id = "limboauth",
    name = "LimboAuth",
    version = BuildConstants.AUTH_VERSION,
    url = "https://elytrium.net/",
    authors = {
      "Elytrium (https://elytrium.net/)",
    },
    dependencies = {@Dependency(id = "limboapi"), @Dependency(id = "floodgate", optional = true)})
public class LimboAuth {

  /** Global rate limiter for certain actions. */
  public static final Ratelimiter<InetAddress> RATELIMITER =
      Ratelimiters.createWithMilliseconds(5000);

  private static Logger S_LOGGER;
  private final Logger logger;

  private final ProxyServer server;
  private final Path dataDirectory;
  private final Metrics.Factory metricsFactory;
  private final PluginContainer pluginContainer;
  private final LimboFactory limboFactory;
  private FloodgateApiHolder floodgateApiHolder;

  private final ConfigManager configManager;
  private final DatabaseService databaseService;
  private final TaskSchedulingService taskSchedulingService;
  private final CacheManager cacheManager;
  private final AuthenticationService authenticationService;
  private final PlayerSessionService playerSessionService;
  private final LimboServerFacade limboServerFacade;
  private final CommandRegistry commandRegistry;

  /**
   * Constructs the LimboAuth plugin instance. Dependencies are injected by Velocity.
   *
   * @param server The ProxyServer instance.
   * @param logger The SLF4J Logger for this this.plugin.
   * @param dataDirectory The plugin's data directory.
   * @param metricsFactory The bStats Metrics factory.
   * @param pluginContainer The PluginContainer for this this.plugin.
   * @param limboFactory The LimboFactory for LimboAPI interactions.
   */
  @Inject
  public LimboAuth(
      ProxyServer server,
      Logger logger,
      @DataDirectory Path dataDirectory,
      Metrics.Factory metricsFactory,
      PluginContainer pluginContainer,
      LimboFactory limboFactory) {
    this.server = server;
    this.logger = logger;
    LimboAuth.S_LOGGER = logger;
    this.dataDirectory = dataDirectory;
    this.metricsFactory = metricsFactory;
    this.pluginContainer = pluginContainer;
    this.limboFactory = limboFactory;

    this.configManager = new ConfigManager(this.logger);
    this.databaseService = new DatabaseService(this.logger);
    this.taskSchedulingService = new TaskSchedulingService(this, this.logger);
    this.cacheManager = new CacheManager(this.logger, this.taskSchedulingService);
    this.limboServerFacade =
        new LimboServerFacade(this, this.logger, this.limboFactory, this.dataDirectory);
    this.playerSessionService =
        new PlayerSessionService(
            this,
            this.logger,
            this.databaseService,
            null,
            this.limboServerFacade,
            this.configManager,
            this.cacheManager);
    this.authenticationService =
        new AuthenticationService(
            this.server,
            this.logger,
            this.databaseService,
            this.cacheManager,
            this.configManager,
            this.limboServerFacade,
            this.playerSessionService);
    this.playerSessionService.setAuthenticationService(this.authenticationService);

    this.commandRegistry =
        new CommandRegistry(
            this,
            this.logger,
            this.server.getCommandManager(),
            this.databaseService,
            this.authenticationService,
            this.playerSessionService,
            this.configManager,
            this.cacheManager,
            this.server);
  }

  /**
   * Called when the proxy initializes. Sets up configurations, database, cache, registers event
   * listeners, commands, and metrics.
   *
   * @param event The ProxyInitializeEvent.
   */
  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    this.logger.info("Enabling LimboAuth v{}...", BuildConstants.AUTH_VERSION);
    System.setProperty("com.j256.simplelogging.level", "ERROR");

    if (this.server.getPluginManager().getPlugin("floodgate").isPresent()) {
      this.floodgateApiHolder = new FloodgateApiHolder();
      this.logger.info("Floodgate API integration enabled.");
    } else {
      this.floodgateApiHolder = null;
      this.logger.info("Floodgate API not found. Bedrock player specific features may be limited.");
    }

    try {
      this.configManager.initialize(this.dataDirectory.resolve("config.yml"));
      net.elytrium.limboauth.event.TaskEvent.reload(this.configManager.getSerializer());
      net.elytrium.limboauth.handler.AuthSessionHandler.reload(
          this.configManager.getSerializer(),
          this.configManager.getSettings(),
          this.configManager.unsafePasswords);
      this.databaseService.initialize(this.dataDirectory, this.configManager);
      this.cacheManager.initialize(this.configManager);
      this.limboServerFacade.initialize(this.configManager);
      this.authenticationService.setFloodgateApi(this.floodgateApiHolder);
      this.playerSessionService.initialize(this);

      this.commandRegistry.initialize();
      this.registerListeners();
      this.setupMetrics();
      this.checkUpdates();

      this.logger.info("LimboAuth v{} has been enabled successfully!", BuildConstants.AUTH_VERSION);
    } catch (Exception e) {
      this.logger.error("Failed to initialize LimboAuth. The plugin will be disabled.", e);
    }
  }

  /**
   * Reloads the plugin: clears tasks, reloads config and database, re-registers commands/listeners,
   * and fires an AuthPluginReloadEvent.
   */
  public void reloadPlugin() {
    this.logger.info("Reloading LimboAuth...");
    EventManager eventManager = this.server.getEventManager();

    this.taskSchedulingService.cancelAllTasks();
    this.limboServerFacade.disposeAuthServer();
    eventManager.unregisterListeners(this.pluginContainer);
    this.commandRegistry.unregisterAllCommands();

    try {
      this.configManager.reload();
      net.elytrium.limboauth.event.TaskEvent.reload(this.configManager.getSerializer());
      net.elytrium.limboauth.handler.AuthSessionHandler.reload(
          this.configManager.getSerializer(),
          this.configManager.getSettings(),
          this.configManager.unsafePasswords);

      this.databaseService.reload(this.configManager);
      this.cacheManager.reload(this.configManager);
      this.limboServerFacade.reload(this.configManager);
      this.authenticationService.setFloodgateApi(this.floodgateApiHolder);
      this.playerSessionService.reload(this.configManager);

      this.commandRegistry.registerAllCommands();
      this.registerListeners();
      this.checkUpdates();

      eventManager.fireAndForget(new AuthPluginReloadEvent());
      this.logger.info("LimboAuth has been reloaded successfully!");
    } catch (Exception e) {
      this.logger.error(
          "Failed to reload LimboAuth. The plugin might be in an inconsistent state.", e);
    }
  }

  /** Registers event listeners for authentication and backend API. */
  private void registerListeners() {
    EventManager eventManager = this.server.getEventManager();
    eventManager.register(
        this.pluginContainer,
        new AuthListener(
            this,
            this.authenticationService,
            this.playerSessionService,
            this.configManager,
            this.databaseService,
            this.cacheManager));

    if (this.configManager.isBackendApiEnabled()) {
      if (BackendEndpointsListener.API_CHANNEL != null) {
        this.server.getChannelRegistrar().register(BackendEndpointsListener.API_CHANNEL);
        eventManager.register(this.pluginContainer, new BackendEndpointsListener(this));
        this.logger.debug("Backend API listener registered.");
      } else {
        this.logger.warn("BackendEndpointsListener.API_CHANNEL is null, cannot register listener.");
      }
    } else {
      if (BackendEndpointsListener.API_CHANNEL != null) {
        this.server.getChannelRegistrar().unregister(BackendEndpointsListener.API_CHANNEL);
      }
      this.logger.debug("Backend API is disabled. Listener not registered or unregistered.");
    }
  }

  /** Sets up bStats metrics charts. */
  private void setupMetrics() {
    this.logger.debug("Setting up bStats metrics...");
    Metrics metrics = this.metricsFactory.make(this.pluginContainer, 13700);
    this.configManager.addMetricsCharts(metrics, this.databaseService);
    this.logger.debug("bStats metrics setup complete.");
  }

  /** Schedules an asynchronous update check if enabled. */
  private void checkUpdates() {
    if (this.configManager.getSettings().MAIN.CHECK_FOR_UPDATES) {
      this.logger.debug("Scheduling update check...");
      this.taskSchedulingService.scheduleOnce(
          () -> {
            if (!this.configManager.checkUpdates()) {
              this.logger.warn("****************************************************************");
              this.logger.warn("* A new version of LimboAuth is available! Please update soon. *");
              this.logger.warn("* Download: https://elytrium.net/downloads/plugins/limboauth *");
              this.logger.warn("****************************************************************");
            } else {
              this.logger.info("LimboAuth is up to date.");
            }
          },
          5,
          TimeUnit.SECONDS);
    } else {
      this.logger.info("Update checking is disabled in the configuration.");
    }
  }

  /**
   * Handles proxy shutdown: cancels tasks and cleans up services.
   *
   * @param event The ProxyShutdownEvent.
   */
  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    this.logger.info("Disabling LimboAuth...");
    this.taskSchedulingService.cancelAllTasks();
    if (this.limboServerFacade != null) {
      this.limboServerFacade.disposeAuthServer();
    }
    if (this.databaseService != null) {
      this.databaseService.closeDataSource();
    }
    this.logger.info("LimboAuth has been disabled successfully.");
  }

  /**
   * Returns the ProxyServer instance.
   *
   * @return The ProxyServer instance.
   */
  public ProxyServer getServer() {
    return this.server;
  }

  /**
   * Returns the SLF4J logger for this this.plugin.
   *
   * @return The SLF4J this.logger.
   */
  public Logger getLogger() {
    return this.logger;
  }

  /**
   * Returns the static SLF4J this.logger.
   *
   * @return The static SLF4J this.logger.
   */
  public static Logger getStaticLogger() {
    return S_LOGGER;
  }

  /**
   * Returns the PluginContainer for this this.plugin.
   *
   * @return The PluginContainer.
   */
  public PluginContainer getPluginContainer() {
    return this.pluginContainer;
  }

  /**
   * Returns the LimboFactory instance for creating Limbo worlds, etc.
   *
   * @return The LimboFactory instance.
   */
  public LimboFactory getLimboFactory() {
    return this.limboFactory;
  }

  /**
   * Returns the Floodgate API holder, if Floodgate is present.
   *
   * @return The FloodgateApiHolder, or null.
   */
  public FloodgateApiHolder getFloodgateApiHolder() {
    return this.floodgateApiHolder;
  }

  /**
   * Returns the ConfigManager handling plugin configuration.
   *
   * @return The ConfigManager.
   */
  public ConfigManager getConfigManager() {
    return this.configManager;
  }

  /**
   * Returns the DatabaseService for player data.
   *
   * @return The DatabaseService.
   */
  public DatabaseService getDatabaseService() {
    return this.databaseService;
  }

  /**
   * Returns the CacheManager for temporary auth data.
   *
   * @return The CacheManager.
   */
  public CacheManager getCacheManager() {
    return this.cacheManager;
  }

  /**
   * Returns the AuthenticationService handling login checks.
   *
   * @return The AuthenticationService.
   */
  public AuthenticationService getAuthenticationService() {
    return this.authenticationService;
  }

  /**
   * Returns the PlayerSessionService managing player sessions.
   *
   * @return The PlayerSessionService.
   */
  public PlayerSessionService getPlayerSessionService() {
    return this.playerSessionService;
  }

  /**
   * Returns the CommandRegistry for registering commands.
   *
   * @return The CommandRegistry.
   */
  public CommandRegistry getCommandRegistry() {
    return this.commandRegistry;
  }

  /**
   * Returns the LimboServerFacade that creates/manages the auth Limbo world.
   *
   * @return The LimboServerFacade.
   */
  public LimboServerFacade getLimboServerFacade() {
    return this.limboServerFacade;
  }

  /**
   * Returns the TaskSchedulingService for delayed tasks.
   *
   * @return The TaskSchedulingService.
   */
  public TaskSchedulingService getTaskSchedulingService() {
    return this.taskSchedulingService;
  }

  /** An in-memory cache entry holding the time of a cached result. */
  public static class CachedUser {
    private final long checkTime;

    /**
     * Constructs a CachedUser.
     *
     * @param checkTime The time this cache entry was created.
     */
    /** Default constructor. */
    public CachedUser(long checkTime) {
      this.checkTime = checkTime;
    }

    /**
     * Returns the creation time of this cache entry.
     *
     * @return The creation time.
     */
    public long getCheckTime() {
      return this.checkTime;
    }
  }

  /** A cache entry for a session user, storing the address and username. */
  public static class CachedSessionUser extends CachedUser {
    private final InetAddress inetAddress;
    private final String username;

    /**
     * Constructs a CachedSessionUser.
     *
     * @param checkTime The time this cache entry was created.
     * @param inetAddress The IP address of the user.
     * @param username The username of the user.
     */
    /** Default constructor. */
    public CachedSessionUser(long checkTime, InetAddress inetAddress, String username) {
      super(checkTime);
      this.inetAddress = inetAddress;
      this.username = username;
    }

    /**
     * Returns the IP address of the cached user.
     *
     * @return The IP address.
     */
    public InetAddress getInetAddress() {
      return this.inetAddress;
    }

    /**
     * Returns the username of the cached user.
     *
     * @return The username.
     */
    public String getUsername() {
      return this.username;
    }
  }

  /** A cache entry for premium players; can mark forced-premium status. */
  public static class CachedPremiumUser extends CachedUser {
    private final boolean premium;
    private boolean forcePremium = false;

    /**
     * Constructs a CachedPremiumUser.
     *
     * @param checkTime The time this cache entry was created.
     * @param premium True if the user is considered premium, false otherwise.
     */
    /** Default constructor. */
    public CachedPremiumUser(long checkTime, boolean premium) {
      super(checkTime);
      this.premium = premium;
    }

    /**
     * Sets whether this premium status was forced (e.g., by internal check).
     *
     * @param forcePremium True to mark as forced premium.
     */
    public void setForcePremium(boolean forcePremium) {
      this.forcePremium = forcePremium;
    }

    /**
     * Checks if this premium status was forced.
     *
     * @return True if this premium status was forced.
     */
    public boolean isForcePremium() {
      return this.forcePremium;
    }

    /**
     * Checks if the user is considered premium.
     *
     * @return True if the user is considered premium.
     */
    public boolean isPremium() {
      return this.premium;
    }
  }

  /** A cache entry for players who may be brute-forcing; stores attempt count. */
  public static class CachedBruteforceUser extends CachedUser {
    private int attempts;

    /**
     * Constructs a CachedBruteforceUser.
     *
     * @param checkTime The time this cache entry was created.
     */
    /** Default constructor. */
    public CachedBruteforceUser(long checkTime) {
      super(checkTime);
      this.attempts = 0;
    }

    /** Increments the login attempt counter. */
    public void incrementAttempts() {
      this.attempts++;
    }

    /**
     * Returns the number of failed login attempts.
     *
     * @return The number of attempts.
     */
    public int getAttempts() {
      return this.attempts;
    }
  }

  /** A wrapper for premium-auth check results, including UUID if applicable. */
  public static class PremiumResponse {
    private final PremiumState state;
    private final UUID uuid;

    /**
     * Constructs a PremiumResponse with state only.
     *
     * @param state The premium state.
     */
    /** Default constructor. */
    public PremiumResponse(PremiumState state) {
      this(state, (UUID) null);
    }

    /**
     * Constructs a PremiumResponse with state and UUID.
     *
     * @param state The premium state.
     * @param uuid The player's UUID if applicable.
     */
    /** Default constructor. */
    public PremiumResponse(PremiumState state, UUID uuid) {
      this.state = state;
      this.uuid = uuid;
    }

    /**
     * Constructs a PremiumResponse with state and UUID string.
     *
     * @param state The premium state.
     * @param uuidStr The player's UUID as a string (dashed or compact).
     * @throws IllegalArgumentException if the UUID string is invalid.
     */
    /** Default constructor. */
    public PremiumResponse(PremiumState state, String uuidStr) {
      this.state = state;
      if (uuidStr == null || uuidStr.isEmpty()) {
        this.uuid = null;
      } else if (uuidStr.contains("-")) {
        try {
          this.uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid UUID string: " + uuidStr, e);
        }
      } else {
        try {
          if (uuidStr.length() != 32) {
            throw new IllegalArgumentException(
                "Compact UUID string must be 32 characters long: " + uuidStr);
          }
          this.uuid =
              new UUID(
                  Long.parseUnsignedLong(uuidStr.substring(0, 16), 16),
                  Long.parseUnsignedLong(uuidStr.substring(16), 16));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid compact UUID string format: " + uuidStr, e);
        }
      }
    }

    /**
     * Returns the premium authentication state.
     *
     * @return The premium state.
     */
    public PremiumState getState() {
      return this.state;
    }

    /**
     * Returns the player's UUID, or null if not applicable.
     *
     * @return The player's UUID.
     */
    public UUID getUuid() {
      return this.uuid;
    }
  }

  /** Enum representing the possible outcomes of a premium authentication check. */
  public enum PremiumState {
    /** Player is confirmed premium (e.g., hash is empty in DB). */
    PREMIUM,
    /** Player's username is premium according to external check, but local state might differ. */
    PREMIUM_USERNAME,
    /** Player is confirmed not premium (cracked). */
    CRACKED,
    /**
     * Premium status is unknown (e.g., player not registered and external check not definitive).
     */
    UNKNOWN,
    /** External premium check API is rate-limited. */
    RATE_LIMIT,
    /** An error occurred during the premium check. */
    ERROR
  }
}
