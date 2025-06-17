package net.elytrium.limboauth.service;

import com.google.inject.Inject;
import com.velocitypowered.api.proxy.Player;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.elytrium.limboauth.LimboAuth;
import org.slf4j.Logger;

/**
 * Manages various caches used by LimboAuth, including authenticated sessions, premium status,
 * brute-force attempts, and pending logins. Also handles scheduled cache purging.
 */
public class CacheManager {

  private final Logger logger;
  private final TaskSchedulingService taskSchedulingService;
  private ConfigManager configManager;

  private final Map<String, LimboAuth.CachedSessionUser> cachedAuthChecks =
      new ConcurrentHashMap<>();
  private final Map<String, LimboAuth.CachedPremiumUser> premiumCache = new ConcurrentHashMap<>();
  private final Map<InetAddress, LimboAuth.CachedBruteforceUser> bruteforceCache =
      new ConcurrentHashMap<>();
  private final Set<String> forcedOfflinePreviously = Collections.synchronizedSet(new HashSet<>());
  private final Set<String> pendingLogins = ConcurrentHashMap.newKeySet();

  private com.velocitypowered.api.scheduler.ScheduledTask purgeAuthCacheTask;
  private com.velocitypowered.api.scheduler.ScheduledTask purgePremiumCacheTask;
  private com.velocitypowered.api.scheduler.ScheduledTask purgeBruteforceCacheTask;

  /**
   * Constructs the CacheManager.
   *
   * @param logger The logger for this service.
   * @param taskSchedulingService Service for scheduling cache purge tasks.
   */
  @Inject
  /** Default constructor. */
  public CacheManager(Logger logger, TaskSchedulingService taskSchedulingService) {
    this.logger = logger;
    this.taskSchedulingService = taskSchedulingService;
  }

  /**
   * Initializes the CacheManager with the given ConfigManager and schedules purge tasks.
   *
   * @param configManager The configuration manager.
   */
  public void initialize(ConfigManager configManager) {
    this.configManager = configManager;
    setupPurgeTasks();
    this.logger.info("CacheManager initialized and purge tasks scheduled.");
  }

  /**
   * Reloads the CacheManager, re-applying configuration for purge tasks.
   *
   * @param newConfigManager The new configuration manager.
   */
  public void reload(ConfigManager newConfigManager) {
    this.configManager = newConfigManager;
    if (purgeAuthCacheTask != null) taskSchedulingService.cancelTask(purgeAuthCacheTask);
    if (purgePremiumCacheTask != null) taskSchedulingService.cancelTask(purgePremiumCacheTask);
    if (purgeBruteforceCacheTask != null)
      taskSchedulingService.cancelTask(purgeBruteforceCacheTask);
    setupPurgeTasks();
    this.logger.info("CacheManager reloaded and purge tasks rescheduled.");
  }

  private void setupPurgeTasks() {
    long purgeCacheMillis = this.configManager.getPurgeCacheMillis();
    if (purgeCacheMillis > 0) {
      purgeAuthCacheTask =
          taskSchedulingService.scheduleRepeatingTask(
              () -> checkCache(this.cachedAuthChecks, purgeCacheMillis, "auth session"),
              purgeCacheMillis,
              purgeCacheMillis);
    }

    long purgePremiumCacheMillis = this.configManager.getPurgePremiumCacheMillis();
    if (purgePremiumCacheMillis > 0) {
      purgePremiumCacheTask =
          taskSchedulingService.scheduleRepeatingTask(
              () -> checkCache(this.premiumCache, purgePremiumCacheMillis, "premium status"),
              purgePremiumCacheMillis,
              purgePremiumCacheMillis);
    }

    long purgeBruteforceCacheMillis = this.configManager.getPurgeBruteforceCacheMillis();
    if (purgeBruteforceCacheMillis > 0) {
      purgeBruteforceCacheTask =
          taskSchedulingService.scheduleRepeatingTask(
              () ->
                  checkCache(
                      this.bruteforceCache, purgeBruteforceCacheMillis, "bruteforce attempt"),
              purgeBruteforceCacheMillis,
              purgeBruteforceCacheMillis);
    }
  }

  private void checkCache(
      Map<?, ? extends LimboAuth.CachedUser> userMap, long time, String cacheName) {
    long currentTime = System.currentTimeMillis();
    int preSize = userMap.size();
    userMap.entrySet().removeIf(entry -> entry.getValue().getCheckTime() + time <= currentTime);
    int postSize = userMap.size();
    if (preSize > postSize) {
      this.logger.debug(
          "Purged {} {} cache entries. {} remaining.", preSize - postSize, cacheName, postSize);
    }
  }

  /**
   * Caches an authenticated user session.
   *
   * @param player The player whose session is to be cached.
   */
  public void cacheAuthUser(Player player) {
    String username = player.getUsername();
    String lowercaseUsername = username.toLowerCase(Locale.ROOT);
    cachedAuthChecks.put(
        lowercaseUsername,
        new LimboAuth.CachedSessionUser(
            System.currentTimeMillis(), player.getRemoteAddress().getAddress(), username));
    this.logger.debug("Cached auth session for user: {}", lowercaseUsername);
  }

  /**
   * Removes a user's authenticated session and premium status from the cache.
   *
   * @param username The username of the player whose cache entries are to be removed.
   */
  public void removeAuthUserFromCache(String username) {
    String lowercaseUsername = username.toLowerCase(Locale.ROOT);
    cachedAuthChecks.remove(lowercaseUsername);
    premiumCache.remove(lowercaseUsername);
    this.logger.debug("Removed auth session and premium cache for user: {}", lowercaseUsername);
  }

  /**
   * Checks if a player needs authentication based on their current session and cached data.
   * Authentication is needed if no session is cached, or if the IP address or username case
   * mismatch.
   *
   * @param player The player to check.
   * @return True if authentication is required, false otherwise.
   */
  public boolean needsAuth(Player player) {
    String username = player.getUsername();
    String lowercaseUsername = username.toLowerCase(Locale.ROOT);
    if (!cachedAuthChecks.containsKey(lowercaseUsername)) {
      return true;
    }
    LimboAuth.CachedSessionUser sessionUser = cachedAuthChecks.get(lowercaseUsername);
    boolean needsAuth =
        !sessionUser.getInetAddress().equals(player.getRemoteAddress().getAddress())
            || !sessionUser.getUsername().equals(username); // Case-sensitive username check
    if (needsAuth) {
      this.logger.debug(
          "Player {} needs auth. IP or username case mismatch. Cached: IP={}, User={}. Current: IP={}, User={}",
          username,
          sessionUser.getInetAddress(),
          sessionUser.getUsername(),
          player.getRemoteAddress().getAddress(),
          username);
    }
    return needsAuth;
  }

  /**
   * Retrieves a premium cache entry for a given nickname.
   *
   * @param nickname The nickname (case-insensitive).
   * @return The {@link LimboAuth.CachedPremiumUser} entry, or null if not found.
   */
  public LimboAuth.CachedPremiumUser getPremiumCacheEntry(String nickname) {
    return premiumCache.get(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Sets a premium cache entry for a given nickname.
   *
   * @param nickname The nickname (case-insensitive).
   * @param isPremium True if the player is premium, false otherwise.
   */
  public void setPremiumCacheEntry(String nickname, boolean isPremium) {
    String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    premiumCache.put(
        lowercaseNickname, new LimboAuth.CachedPremiumUser(System.currentTimeMillis(), isPremium));
    this.logger.debug("Set premium cache for {}: {}", lowercaseNickname, isPremium);
  }

  /**
   * Sets a premium cache entry for a lowercased nickname, marking it as "forced premium".
   *
   * @param lowercasedNickname The already lowercased nickname.
   * @param value True if the player is premium, false otherwise.
   * @return The created {@link LimboAuth.CachedPremiumUser} entry.
   */
  public LimboAuth.CachedPremiumUser setForcedPremiumCacheLowercased(
      String lowercasedNickname, boolean value) {
    LimboAuth.CachedPremiumUser premiumUser =
        new LimboAuth.CachedPremiumUser(System.currentTimeMillis(), value);
    premiumUser.setForcePremium(value);
    this.premiumCache.put(lowercasedNickname, premiumUser);
    this.logger.debug("Set forced premium cache for {}: {}", lowercasedNickname, value);
    return premiumUser;
  }

  /**
   * Increments the brute-force attempt counter for a given IP address.
   *
   * @param address The IP address.
   */
  public void incrementBruteforceAttempts(InetAddress address) {
    bruteforceCache
        .computeIfAbsent(
            address, k -> new LimboAuth.CachedBruteforceUser(System.currentTimeMillis()))
        .incrementAttempts();
    this.logger.debug(
        "Incremented bruteforce attempts for IP: {}. Total: {}",
        address,
        getBruteforceAttempts(address));
  }

  /**
   * Gets the number of brute-force attempts recorded for a given IP address.
   *
   * @param address The IP address.
   * @return The number of attempts, or 0 if none recorded.
   */
  public int getBruteforceAttempts(InetAddress address) {
    LimboAuth.CachedBruteforceUser user = bruteforceCache.get(address);
    return (user == null) ? 0 : user.getAttempts();
  }

  /**
   * Clears the brute-force attempt counter for a given IP address.
   *
   * @param address The IP address.
   */
  public void clearBruteforceAttempts(InetAddress address) {
    bruteforceCache.remove(address);
    this.logger.debug("Cleared bruteforce attempts for IP: {}", address);
  }

  /**
   * Marks a player (by nickname) as having previously connected in forced offline mode. This is
   * used in scenarios where strict online mode checks might be temporarily bypassed.
   *
   * @param nickname The player's nickname (case-insensitive).
   */
  public void saveForceOfflineMode(String nickname) {
    this.forcedOfflinePreviously.add(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Removes the "forced offline previously" mark for a player.
   *
   * @param nickname The player's nickname (case-insensitive).
   */
  public void unsetForcedOfflinePreviously(String nickname) {
    this.forcedOfflinePreviously.remove(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Checks if a player was marked as having previously connected in forced offline mode.
   *
   * @param nickname The player's nickname (case-insensitive).
   * @return True if the mark exists, false otherwise.
   */
  public boolean isForcedOfflinePreviously(String nickname) {
    return this.forcedOfflinePreviously.contains(nickname.toLowerCase(Locale.ROOT));
  }

  /**
   * Adds a username to the set of players whose login is pending (e.g., awaiting external premium
   * check).
   *
   * @param username The username (case-insensitive).
   */
  public void addPendingLogin(String username) {
    this.pendingLogins.add(username.toLowerCase(Locale.ROOT));
  }

  /**
   * Removes a username from the set of pending logins.
   *
   * @param username The username (case-insensitive).
   * @return True if the username was present and removed, false otherwise.
   */
  public boolean removePendingLogin(String username) {
    return this.pendingLogins.remove(username.toLowerCase(Locale.ROOT));
  }

  /**
   * Checks if a username is in the set of pending logins.
   *
   * @param username The username (case-insensitive).
   * @return True if the login is pending, false otherwise.
   */
  public boolean isPendingLogin(String username) {
    return this.pendingLogins.contains(username.toLowerCase(Locale.ROOT));
  }

  /** Clears all managed caches. */
  public void clearAllCaches() {
    cachedAuthChecks.clear();
    premiumCache.clear();
    bruteforceCache.clear();
    forcedOfflinePreviously.clear();
    pendingLogins.clear();
    this.logger.info("All caches have been cleared.");
  }
}
