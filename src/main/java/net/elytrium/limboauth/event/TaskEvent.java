package net.elytrium.limboauth.event;

import java.util.function.Consumer;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.Settings;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for LimboAuth events that represent a task or a stage in an authentication
 * flow, which can be completed, cancelled, or indicate a bypass.
 *
 * <p>These events are often asynchronous, allowing handlers to perform operations without blocking
 * the main server thread. The {@link #complete(Result)} or {@link #cancel(Component)} methods
 * should be called by handlers to signal the outcome of their processing.
 */
public abstract class TaskEvent {

  private static Component DEFAULT_REASON;

  private final Consumer<TaskEvent> onComplete;
  private Result result = Result.NORMAL;
  private Component reason; // Initialized with DEFAULT_REASON

  /**
   * Constructs a TaskEvent with a normal initial result and a callback for completion.
   *
   * @param onComplete A consumer to be called when this event's processing is finished or
   *     cancelled.
   */
  /** Default constructor. */
  public TaskEvent(Consumer<TaskEvent> onComplete) {
    this.onComplete = onComplete;
    this.reason = DEFAULT_REASON; // Initialize with the static default
  }

  /**
   * Constructs a TaskEvent with a specified initial result and a callback for completion.
   *
   * @param onComplete A consumer to be called when this event's processing is finished or
   *     cancelled.
   * @param result The initial {@link Result} of this event.
   */
  /** Default constructor. */
  public TaskEvent(Consumer<TaskEvent> onComplete, Result result) {
    this.onComplete = onComplete;
    this.result = result;
    this.reason = DEFAULT_REASON; // Initialize with the static default
  }

  /**
   * Marks this event as completed with the specified result. If the event was in a {@link
   * Result#WAIT} state, this will trigger the {@code onComplete} callback.
   *
   * @param result The final {@link Result} of the event processing. Cannot be null.
   */
  public void complete(@NotNull Result result) {
    if (this.result == Result.WAIT) {
      this.result = result;
      if (this.onComplete != null) {
        this.onComplete.accept(this);
      }
    }
  }

  /**
   * Marks this event as cancelled with a specific reason and completes it. If the event was in a
   * {@link Result#WAIT} state, this will set the result to {@link Result#CANCEL}, store the reason,
   * and trigger the {@code onComplete} callback.
   *
   * @param reason The {@link Component} explaining why the event was cancelled. Cannot be null.
   */
  public void completeAndCancel(@NotNull Component reason) {
    if (this.result == Result.WAIT) {
      this.cancel(reason);
      if (this.onComplete != null) {
        this.onComplete.accept(this);
      }
    }
  }

  /**
   * Cancels this event, setting its result to {@link Result#CANCEL} and providing a reason.
   *
   * @param reason The {@link Component} explaining why the event is being cancelled. Cannot be
   *     null.
   */
  public void cancel(@NotNull Component reason) {
    this.result = Result.CANCEL;
    this.reason = reason;
  }

  /**
   * Sets the result of this event.
   *
   * @param result The new {@link Result}. Cannot be null.
   */
  public void setResult(@NotNull Result result) {
    this.result = result;
  }

  /**
   * Gets the current result of this event.
   *
   * @return The current {@link Result}.
   */
  public Result getResult() {
    return this.result;
  }

  /**
   * Gets the reason for cancellation, if this event was cancelled.
   *
   * @return The cancellation reason {@link Component}, or the default reason if not explicitly set.
   */
  public Component getReason() {
    return this.reason;
  }

  /**
   * Reloads static resources for TaskEvents, specifically the default cancellation reason message.
   * This should be called when the plugin's configuration (and thus, its serializer) is reloaded.
   *
   * @param serializer The serializer instance from ConfigManager, used to deserialize the default
   *     reason string.
   */
  public static void reload(Serializer serializer) {
    DEFAULT_REASON = serializer.deserialize(Settings.IMP.MAIN.STRINGS.EVENT_CANCELLED);
  }

  /** Enum representing the possible outcomes or states of a {@link TaskEvent}. */
  public enum Result {
    /** The event/task was cancelled. */
    CANCEL,
    /** The event/task indicates that the standard flow should be bypassed. */
    BYPASS,
    /** The event/task should proceed normally. */
    NORMAL,
    /** The event/task is waiting for an asynchronous operation to complete. */
    WAIT
  }
}
