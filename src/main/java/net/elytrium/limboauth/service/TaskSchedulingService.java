package net.elytrium.limboauth.service;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboauth.LimboAuth;
import org.slf4j.Logger;

/**
 * Service for scheduling tasks using Velocity's scheduler. It provides methods for scheduling
 * repeating tasks, delayed tasks, and manages a list of scheduled tasks for cancellation.
 */
public class TaskSchedulingService {

  private final Scheduler scheduler;
  private final LimboAuth plugin;
  private final PluginContainer pluginContainer;
  private final Logger logger;
  private final List<ScheduledTask> scheduledTasks = new ArrayList<>();

  /**
   * Constructs the TaskSchedulingService. Dependencies are injected.
   *
   * @param plugin The main LimboAuth plugin instance, used to get the plugin container and
   *     scheduler.
   * @param logger The logger for this service.
   */
  @Inject
  /** Default constructor. */
  public TaskSchedulingService(LimboAuth plugin, Logger logger) {
    this.plugin = plugin;
    this.pluginContainer = this.plugin.getPluginContainer();
    this.scheduler =
        this.plugin.getServer().getScheduler(); // Pobierz Scheduler z instancji ProxyServer
    this.logger = logger;
  }

  /**
   * Schedules a task to run repeatedly.
   *
   * @param task The {@link Runnable} to execute.
   * @param delayMillis The initial delay in milliseconds before the first execution.
   * @param repeatMillis The interval in milliseconds between subsequent executions.
   * @return The {@link ScheduledTask} representing this scheduled task.
   */
  public ScheduledTask scheduleRepeatingTask(Runnable task, long delayMillis, long repeatMillis) {
    ScheduledTask scheduledTask =
        scheduler
            .buildTask(pluginContainer, task)
            .delay(delayMillis, TimeUnit.MILLISECONDS)
            .repeat(repeatMillis, TimeUnit.MILLISECONDS)
            .schedule();
    scheduledTasks.add(scheduledTask);
    this.logger.debug(
        "Scheduled repeating task with delay {}ms and repeat {}ms", delayMillis, repeatMillis);
    return scheduledTask;
  }

  /**
   * Schedules a task to run once after a specified delay.
   *
   * @param task The {@link Runnable} to execute.
   * @param delayMillis The delay in milliseconds before execution.
   * @return The {@link ScheduledTask} representing this scheduled task.
   */
  public ScheduledTask scheduleDelayedTask(Runnable task, long delayMillis) {
    ScheduledTask scheduledTask =
        scheduler
            .buildTask(pluginContainer, task)
            .delay(delayMillis, TimeUnit.MILLISECONDS)
            .schedule();
    // Does not add to scheduledTasks list as it's a one-off and usually not globally managed for
    // cancellation.
    this.logger.debug("Scheduled delayed task with delay {}ms", delayMillis);
    return scheduledTask;
  }

  /**
   * Schedules a task to run once after a specified delay with a given time unit. This task is
   * tracked for potential cancellation via {@link #cancelAllTasks()}.
   *
   * @param task The {@link Runnable} to execute.
   * @param delay The delay before execution.
   * @param unit The {@link TimeUnit} for the delay.
   * @return The {@link ScheduledTask} representing this scheduled task.
   */
  public ScheduledTask scheduleOnce(Runnable task, long delay, TimeUnit unit) {
    ScheduledTask scheduled =
        scheduler.buildTask(pluginContainer, task).delay(delay, unit).schedule();
    scheduledTasks.add(scheduled); // Track this task
    this.logger.debug("Scheduled one-time task with delay {} {}", delay, unit.toString());
    return scheduled;
  }

  /**
   * Cancels a specific scheduled task. If the task was tracked by this service, it will also be
   * removed from the internal list.
   *
   * @param task The {@link ScheduledTask} to cancel.
   */
  public void cancelTask(ScheduledTask task) {
    if (task != null) {
      task.cancel();
      scheduledTasks.remove(task);
      this.logger.debug("Cancelled a scheduled task.");
    }
  }

  /** Cancels all tasks that were scheduled through this service and are currently tracked. */
  public void cancelAllTasks() {
    for (ScheduledTask task :
        new ArrayList<>(
            scheduledTasks)) { // Iterate over a copy to avoid ConcurrentModificationException
      task.cancel();
    }
    scheduledTasks.clear();
    this.logger.info("All tracked scheduled tasks have been cancelled.");
  }

  /**
   * Placeholder method for rescheduling tasks. Currently, task rescheduling is handled by
   * individual services re-registering their tasks after a configuration reload.
   *
   * @param configManager The {@link ConfigManager} instance, potentially used to get new scheduling
   *     parameters.
   */
  public void rescheduleAllTasks(ConfigManager configManager) {
    // This method is not currently used for automatic re-scheduling of all tasks.
    // Services like CacheManager should re-register their purge tasks themselves upon reload.
    this.logger.info(
        "Task rescheduling logic is typically handled by individual services calling scheduling methods again upon reload.");
  }
}
