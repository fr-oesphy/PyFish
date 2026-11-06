package fr.oesphy.pyfish.event;

/**
 * The result of firing a {@link PyEvent} at its registered listeners.
 *
 * <p>Mirrors the semantics used by both Fabric's event result type and
 * NeoForge's {@code EventResult}, giving PyFish a loader-agnostic vocabulary.
 *
 * <ul>
 *   <li>{@link #PASS}    — no listener changed the outcome; proceed normally.</li>
 *   <li>{@link #CANCEL}  — a listener requested cancellation; suppress the action.</li>
 *   <li>{@link #SUCCESS} — a listener handled the event successfully; may short-circuit
 *       vanilla processing on some loaders.</li>
 *   <li>{@link #CONSUME} — the event was fully consumed; propagation stops immediately.</li>
 * </ul>
 *
 * <p>Scripts interact with event results primarily through {@link PyEvent#cancel()}.
 * Returning the string {@code "cancel"} from a handler is also accepted as a
 * convenience and is converted by {@link EventHandler} to {@link #CANCEL}.
 */
public enum EventResult {

    /**
     * The event proceeds as normal; no listener modified the outcome.
     * This is the default result when no listener calls {@link PyEvent#cancel()}
     * or returns a result string.
     */
    PASS,

    /**
     * The event has been cancelled.  For cancellable events this suppresses the
     * associated game action (e.g. block break, chat message delivery).
     */
    CANCEL,

    /**
     * A listener positively handled the event.  Equivalent to Fabric's
     * {@code ActionResult.SUCCESS} — may cause vanilla to skip its own handling
     * depending on the event type.
     */
    SUCCESS,

    /**
     * The event was fully consumed.  No further processing should occur, even
     * by vanilla code that would normally run regardless of cancellation.
     */
    CONSUME;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns {@code true} if this result represents a cancelled or consumed event. */
    public boolean isCancelled() {
        return this == CANCEL || this == CONSUME;
    }

    /**
     * Parses a Python handler's string return value into an {@link EventResult}.
     *
     * <p>Accepted strings (case-insensitive): {@code "cancel"}, {@code "success"},
     * {@code "consume"}, {@code "pass"}.  Any other value returns {@code null},
     * indicating the return value should be ignored.
     *
     * @param value the string returned by a Python handler
     * @return the matching {@link EventResult}, or {@code null} if unrecognised
     */
    public static EventResult fromString(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(java.util.Locale.ROOT).strip()) {
            case "cancel"  -> CANCEL;
            case "success" -> SUCCESS;
            case "consume" -> CONSUME;
            case "pass"    -> PASS;
            default        -> null;
        };
    }
}
