package fr.oesphy.pyfish.api.util;

import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick-aware task scheduler for PyFish scripts.
 *
 * <p>Tasks can be scheduled with a delay measured in game ticks.  Calling
 * {@link #tick()} once per server/client tick advances the internal clock and
 * executes all tasks whose target tick has been reached.
 *
 * <p>Two public singletons are provided:
 * <ul>
 *   <li>{@link #SERVER} — driven by server-tick events</li>
 *   <li>{@link #CLIENT} — driven by client-tick events</li>
 * </ul>
 *
 * <p>Both GraalPy {@link Value} callbacks and plain Java {@link Runnable} tasks
 * are supported to allow easy unit-testing without a live GraalPy context.
 */
public final class TickScheduler {

    // -------------------------------------------------------------------------
    // Public singletons
    // -------------------------------------------------------------------------

    /** Singleton driven by server-tick events. */
    public static final TickScheduler SERVER = new TickScheduler();

    /** Singleton driven by client-tick events. */
    public static final TickScheduler CLIENT = new TickScheduler();

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private static final Logger LOGGER = LoggerFactory.getLogger(TickScheduler.class);

    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicLong nextId = new AtomicLong(0);

    /** Internal task container — either a {@link Value} or a {@link Runnable}. */
    private record ScheduledTask(long targetTick, long id, Object callback)
            implements Comparable<ScheduledTask> {
        @Override
        public int compareTo(ScheduledTask other) {
            int cmp = Long.compare(this.targetTick, other.targetTick);
            if (cmp != 0) return cmp;
            return Long.compare(this.id, other.id);
        }
    }

    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>();

    // -------------------------------------------------------------------------
    // Scheduling — Python-facing (Value)
    // -------------------------------------------------------------------------

    /**
     * Schedules a GraalPy {@link Value} callback to be invoked after {@code delayTicks} ticks.
     *
     * @param callback   a callable Python {@link Value}
     * @param delayTicks ticks to wait (0 = next tick)
     * @return a task id that can be passed to {@link #cancel(long)}
     */
    public long schedule(Value callback, int delayTicks) {
        return enqueue(callback, delayTicks);
    }

    /**
     * Schedules a GraalPy {@link Value} callback to be invoked on the next tick.
     *
     * @param callback a callable Python {@link Value}
     * @return a task id
     */
    public long schedule(Value callback) {
        return schedule(callback, 0);
    }

    // -------------------------------------------------------------------------
    // Scheduling — Java-facing (Runnable) — primarily for unit tests
    // -------------------------------------------------------------------------

    /**
     * Schedules a Java {@link Runnable} to be invoked after {@code delayTicks} ticks.
     *
     * @param task       the task to run
     * @param delayTicks ticks to wait (0 = next tick)
     * @return a task id that can be passed to {@link #cancel(long)}
     */
    public long schedule(Runnable task, int delayTicks) {
        return enqueue(task, delayTicks);
    }

    /**
     * Schedules a Java {@link Runnable} to be invoked on the next tick.
     *
     * @param task the task to run
     * @return a task id
     */
    public long schedule(Runnable task) {
        return schedule(task, 0);
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    /**
     * Cancels the task with the given id.
     *
     * @param taskId the id returned by {@code schedule}
     */
    public synchronized void cancel(long taskId) {
        queue.removeIf(t -> t.id() == taskId);
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    /**
     * Advances the internal tick counter by one and executes all tasks whose
     * target tick has been reached.
     *
     * <p>Exceptions thrown by individual tasks are caught and logged, so one
     * failing task does not prevent others from running.
     */
    public void tick() {
        long tick = currentTick.incrementAndGet();
        while (true) {
            ScheduledTask task;
            synchronized (this) {
                if (queue.isEmpty() || queue.peek().targetTick() > tick) break;
                task = queue.poll();
            }
            try {
                runTask(task);
            } catch (Exception e) {
                LOGGER.error("[PyFish/TickScheduler] Task {} threw an exception: {}", task.id(), e.getMessage(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Introspection
    // -------------------------------------------------------------------------

    /** @return the current tick count */
    public long currentTick() {
        return currentTick.get();
    }

    /** @return the number of tasks waiting to be executed */
    public synchronized int pendingCount() {
        return queue.size();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private synchronized long enqueue(Object callback, int delayTicks) {
        long id = nextId.getAndIncrement();
        long target = currentTick.get() + Math.max(0, delayTicks);
        queue.add(new ScheduledTask(target, id, callback));
        return id;
    }

    private static void runTask(ScheduledTask task) {
        Object cb = task.callback();
        if (cb instanceof Runnable r) {
            r.run();
        } else if (cb instanceof Value v) {
            if (v.canExecute()) {
                v.executeVoid();
            } else {
                LOGGER.warn("[PyFish/TickScheduler] Task {} callback is not executable", task.id());
            }
        } else if (cb != null) {
            LOGGER.warn("[PyFish/TickScheduler] Task {} has unknown callback type: {}", task.id(), cb.getClass());
        }
    }
}
