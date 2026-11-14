package fr.oesphy.pyfish.event;

import java.util.Objects;

/**
 * Base class for all events fired at Python script listeners.
 *
 * <p>A {@code PyEvent} is a mutable value object passed to each matching
 * listener in turn.  Listeners can inspect fields, call {@link #cancel()} on
 * cancellable events, or set an explicit {@link EventResult} via
 * {@link #setResult(EventResult)}.
 *
 * <h3>Cancellation</h3>
 * <p>Not all events are cancellable.  Attempting to cancel a non-cancellable
 * event throws {@link UnsupportedOperationException}.  Scripts should check
 * {@link #isCancellable()} before calling {@link #cancel()}, or simply call
 * it inside a {@code try/except} if they don't care about the cancellability.
 *
 * <h3>Result coercion from Python return values</h3>
 * <p>{@link EventHandler} inspects the return value of each Python listener.
 * Returning the strings {@code "cancel"}, {@code "success"}, or
 * {@code "consume"} has the same effect as calling the corresponding method.
 * Returning {@code None} (the Python default) is treated as {@link EventResult#PASS}.
 *
 * <h3>Thread safety</h3>
 * <p>Events are not thread-safe.  Each event object is created fresh for one
 * invocation cycle and must only be used on the thread that owns the
 * {@link fr.oesphy.pyfish.script.PyRuntime}.
 *
 * @see EventResult
 * @see EventHandler
 */
public class PyEvent {

    private final boolean cancellable;
    private EventResult result;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a non-cancellable event with result {@link EventResult#PASS}.
     */
    public PyEvent() {
        this(false);
    }

    /**
     * Creates an event with result {@link EventResult#PASS}.
     *
     * @param cancellable whether listeners may call {@link #cancel()} on this event
     */
    public PyEvent(boolean cancellable) {
        this.cancellable = cancellable;
        this.result      = EventResult.PASS;
    }

    // -------------------------------------------------------------------------
    // Public API — available to Python scripts
    // -------------------------------------------------------------------------

    /**
     * Cancels this event, preventing the associated game action from executing.
     *
     * <p>Sets the result to {@link EventResult#CANCEL}.
     *
     * @throws UnsupportedOperationException if this event is not cancellable
     */
    public void cancel() {
        if (!cancellable) {
            throw new UnsupportedOperationException(
                    "Event " + getClass().getSimpleName() + " is not cancellable");
        }
        this.result = EventResult.CANCEL;
    }

    /**
     * Sets the result to {@link EventResult#SUCCESS}.
     * Valid for both cancellable and non-cancellable events.
     */
    public void succeed() {
        this.result = EventResult.SUCCESS;
    }

    /**
     * Sets the result to {@link EventResult#CONSUME}.
     * Valid for both cancellable and non-cancellable events.
     */
    public void consume() {
        this.result = EventResult.CONSUME;
    }

    // -------------------------------------------------------------------------
    // Result accessors
    // -------------------------------------------------------------------------

    /** Returns the current {@link EventResult} for this event. */
    public EventResult result() {
        return result;
    }

    /**
     * Sets the event result explicitly.
     *
     * <p>Prefer {@link #cancel()}, {@link #succeed()}, or {@link #consume()} in
     * scripts; this setter is intended for internal use by {@link EventHandler}.
     */
    public void setResult(EventResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    /** Returns {@code true} if the current result is {@link EventResult#CANCEL} or {@link EventResult#CONSUME}. */
    public boolean isCancelled() {
        return result.isCancelled();
    }

    /** Returns {@code true} if listeners may call {@link #cancel()} on this event. */
    public boolean isCancellable() {
        return cancellable;
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{cancellable=" + cancellable + ", result=" + result + '}';
    }
}
