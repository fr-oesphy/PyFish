package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Console API for Python scripts with file/line tracking and ring buffer.
 * Provides print, log, warn, and error methods that include source location.
 */
public class ConsoleApi {
    private static final Logger LOGGER = PyFish.LOGGER;
    private static final int RING_BUFFER_SIZE = 1000;

    private final List<ConsoleEntry> ringBuffer;
    private int ringBufferIndex;

    public ConsoleApi() {
        this.ringBuffer = new ArrayList<>(RING_BUFFER_SIZE);
        this.ringBufferIndex = 0;
    }

    /**
     * Logs a message at INFO level with optional file/line information.
     */
    public void log(String message, String file, int line) {
        logWithLocation(message, file, line, LogLevel.INFO);
    }

    /**
     * Logs a message at INFO level (no location).
     */
    public void log(String message) {
        log(message, null, -1);
    }

    /**
     * Logs a message at WARN level with optional file/line information.
     */
    public void warn(String message, String file, int line) {
        logWithLocation(message, file, line, LogLevel.WARN);
    }

    /**
     * Logs a message at WARN level (no location).
     */
    public void warn(String message) {
        warn(message, null, -1);
    }

    /**
     * Logs a message at ERROR level with optional file/line information.
     */
    public void error(String message, String file, int line) {
        logWithLocation(message, file, line, LogLevel.ERROR);
    }

    /**
     * Logs a message at ERROR level (no location).
     */
    public void error(String message) {
        error(message, null, -1);
    }

    /**
     * Prints a message (alias for log).
     */
    public void print(String message, String file, int line) {
        log(message, file, line);
    }

    /**
     * Prints a message (alias for log, no location).
     */
    public void print(String message) {
        log(message);
    }

    private void logWithLocation(String message, String file, int line, LogLevel level) {
        String formattedMessage = formatMessage(message, file, line);
        addToRingBuffer(formattedMessage, level);

        switch (level) {
            case INFO:
                LOGGER.info(formattedMessage);
                break;
            case WARN:
                LOGGER.warn(formattedMessage);
                break;
            case ERROR:
                LOGGER.error(formattedMessage);
                break;
        }
    }

    private String formatMessage(String message, String file, int line) {
        if (file != null && !file.isEmpty() && line > 0) {
            return "[PyFish/Script] " + file + ":" + line + " - " + message;
        } else if (file != null && !file.isEmpty()) {
            return "[PyFish/Script] " + file + " - " + message;
        }
        return "[PyFish/Script] " + message;
    }

    private void addToRingBuffer(String message, LogLevel level) {
        ConsoleEntry entry = new ConsoleEntry(message, level, System.currentTimeMillis());

        if (ringBuffer.size() < RING_BUFFER_SIZE) {
            ringBuffer.add(entry);
        } else {
            ringBuffer.set(ringBufferIndex, entry);
            ringBufferIndex = (ringBufferIndex + 1) % RING_BUFFER_SIZE;
        }
    }

    /**
     * Gets all entries from the ring buffer.
     */
    public List<ConsoleEntry> getRingBuffer() {
        return new ArrayList<>(ringBuffer);
    }

    /**
     * Gets the last N entries from the ring buffer.
     */
    public List<ConsoleEntry> getLastEntries(int count) {
        int size = ringBuffer.size();
        if (size == 0) {
            return new ArrayList<>();
        }

        int start = Math.max(0, size - count);
        return new ArrayList<>(ringBuffer.subList(start, size));
    }

    /**
     * Clears the ring buffer.
     */
    public void clearRingBuffer() {
        ringBuffer.clear();
        ringBufferIndex = 0;
    }

    /**
     * Log level enum.
     */
    public enum LogLevel {
        INFO,
        WARN,
        ERROR
    }

    /**
     * Represents a single console entry.
     */
    public static class ConsoleEntry {
        private final String message;
        private final LogLevel level;
        private final long timestamp;

        public ConsoleEntry(String message, LogLevel level, long timestamp) {
            this.message = message;
            this.level = level;
            this.timestamp = timestamp;
        }

        public String getMessage() {
            return message;
        }

        public LogLevel getLevel() {
            return level;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "[" + level + "] " + message;
        }
    }
}
