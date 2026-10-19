package fr.oesphy.pyfish.event;

/**
 * Represents the result of an event handler.
 * Handlers can return these to control event flow.
 */
public enum EventResult {
    /**
     * Pass the event to the next handler.
     * Default behavior if no explicit result is returned.
     */
    PASS,

    /**
     * Cancel the event, preventing further processing.
     * For cancellable events, this prevents the default action.
     */
    CANCEL,

    /**
     * Success - the event was handled successfully.
     * May prevent further processing depending on the event type.
     */
    SUCCESS,

    /**
     * Consume the event - handled and no further processing needed.
     * Similar to SUCCESS but with stronger semantics.
     */
    CONSUME;

    /**
     * Checks if this result should stop event propagation.
     */
    public boolean stopsPropagation() {
        return this == CANCEL || this == SUCCESS || this == CONSUME;
    }

    /**
     * Checks if this result cancels the event.
     */
    public boolean isCancel() {
        return this == CANCEL;
    }
}
