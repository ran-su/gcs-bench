package com.google.cloud.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced StatWatcher that tracks detailed metrics for each operation.
 * Matches C++ RunnerWatcher metrics.
 */
public class StatWatcher implements RunnerWatcher {
    /**
     * Detailed operation record for result persistence.
     */
    public static class OperationRecord {
        public final long timestampMs;
        public final long latencyMs;
        public final long bytes;
        public final boolean success;
        public final int threadId;
        public final int channelId;
        public final String peer;
        public final String object;
        public final String errorCode;
        public final String errorMessage;
        public final List<ChunkRecord> chunks;

        public OperationRecord(
                long timestampMs,
                long latencyMs,
                long bytes,
                boolean success,
                int threadId,
                int channelId,
                String peer,
                String object,
                String errorCode,
                String errorMessage,
                List<ChunkRecord> chunks) {
            this.timestampMs = timestampMs;
            this.latencyMs = latencyMs;
            this.bytes = bytes;
            this.success = success;
            this.threadId = threadId;
            this.channelId = channelId;
            this.peer = peer != null ? peer : "";
            this.object = object != null ? object : "";
            this.errorCode = errorCode != null ? errorCode : "";
            this.errorMessage = errorMessage != null ? errorMessage : "";
            this.chunks = chunks != null ? new ArrayList<>(chunks) : new ArrayList<>();
        }
    }

    private final List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
    private final List<OperationRecord> operations = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong totalBytes = new AtomicLong(0);
    private volatile long startTimeMs = 0;

    @Override
    public void notifyCompleted(
            int threadId,
            int channelId,
            String peer,
            String object,
            long latencyMs,
            long bytes,
            boolean success,
            String errorCode,
            String errorMessage,
            List<ChunkRecord> chunks) {

        long timestampMs = System.currentTimeMillis();

        // Initialize start time on first operation
        if (startTimeMs == 0) {
            synchronized (this) {
                if (startTimeMs == 0) {
                    startTimeMs = timestampMs;
                }
            }
        }

        if (success) {
            latencies.add((double) latencyMs);
            totalBytes.addAndGet(bytes);
        }

        // Track detailed record
        operations.add(new OperationRecord(
                timestampMs, latencyMs, bytes, success,
                threadId, channelId, peer, object,
                errorCode, errorMessage, chunks));
    }

    public List<Double> getLatencies() {
        return latencies;
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public List<OperationRecord> getOperations() {
        return new ArrayList<>(operations);
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }
}
