package fr.oesphy.pyfish.runtime;

import fr.oesphy.pyfish.PyFish;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task scheduler for executing Python code safely on the main game thread.
 * Provides delayed tasks, repeating tasks, and tick-based scheduling.
 */
public class TaskScheduler {
    private static final Logger LOGGER = PyFish.LOGGER;

    private final Map<Long, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private final List<ScheduledTask> tickTasks = new ArrayList<>();

    /**
     * Schedules a task to run after a delay in ticks.
     * @param server The Minecraft server
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     * @param runnable The task to run
     * @return Task ID for cancellation
     */
    public long scheduleDelayedTask(MinecraftServer server, long delayTicks, Runnable runnable) {
        long taskId = taskIdCounter.incrementAndGet();
        ScheduledTask task = new ScheduledTask(taskId, server, delayTicks, false, runnable);
        scheduledTasks.put(taskId, task);
        LOGGER.debug("[PyFish/Scheduler] Scheduled delayed task {} in {} ticks", taskId, delayTicks);
        return taskId;
    }

    /**
     * Schedules a repeating task.
     * @param server The Minecraft server
     * @param intervalTicks Interval in ticks
     * @param runnable The task to run
     * @return Task ID for cancellation
     */
    public long scheduleRepeatingTask(MinecraftServer server, long intervalTicks, Runnable runnable) {
        long taskId = taskIdCounter.incrementAndGet();
        ScheduledTask task = new ScheduledTask(taskId, server, intervalTicks, true, runnable);
        scheduledTasks.put(taskId, task);
        LOGGER.debug("[PyFish/Scheduler] Scheduled repeating task {} every {} ticks", taskId, intervalTicks);
        return taskId;
    }

    /**
     * Cancels a scheduled task.
     * @param taskId The task ID to cancel
     * @return true if the task was found and cancelled
     */
    public boolean cancelTask(long taskId) {
        ScheduledTask removed = scheduledTasks.remove(taskId);
        if (removed != null) {
            LOGGER.debug("[PyFish/Scheduler] Cancelled task {}", taskId);
            return true;
        }
        return false;
    }

    /**
     * Cancels all scheduled tasks.
     */
    public void cancelAllTasks() {
        int count = scheduledTasks.size();
        scheduledTasks.clear();
        LOGGER.info("[PyFish/Scheduler] Cancelled all {} tasks", count);
    }

    /**
     * Called each server tick to update scheduled tasks.
     * Should be registered via the on_server_tick event.
     */
    public void onServerTick(MinecraftServer server) {
        if (scheduledTasks.isEmpty()) {
            return;
        }

        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, ScheduledTask> entry : scheduledTasks.entrySet()) {
            ScheduledTask task = entry.getValue();
            if (task.server != server) {
                continue; // Task belongs to a different server
            }

            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                try {
                    task.runnable.run();
                    if (task.repeating) {
                        task.ticksRemaining = task.intervalTicks;
                    } else {
                        toRemove.add(entry.getKey());
                    }
                } catch (Exception e) {
                    LOGGER.error("[PyFish/Scheduler] Error executing task {}: {}", entry.getKey(), e.getMessage(), e);
                    toRemove.add(entry.getKey());
                }
            }
        }

        for (Long taskId : toRemove) {
            scheduledTasks.remove(taskId);
        }
    }

    /**
     * Gets the number of currently scheduled tasks.
     */
    public int getTaskCount() {
        return scheduledTasks.size();
    }

    private static class ScheduledTask {
        final long taskId;
        final MinecraftServer server;
        final long intervalTicks;
        final boolean repeating;
        final Runnable runnable;
        long ticksRemaining;

        ScheduledTask(long taskId, MinecraftServer server, long intervalTicks, boolean repeating, Runnable runnable) {
            this.taskId = taskId;
            this.server = server;
            this.intervalTicks = intervalTicks;
            this.repeating = repeating;
            this.runnable = runnable;
            this.ticksRemaining = intervalTicks;
        }
    }
}
