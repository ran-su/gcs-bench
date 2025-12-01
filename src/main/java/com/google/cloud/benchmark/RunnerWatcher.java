package com.google.cloud.benchmark;

import java.util.List;

/**
 * Interface for watching benchmark operations and collecting metrics.
 * Matches C++ RunnerWatcher pattern.
 */
public interface RunnerWatcher {

    /**
     * Chunk timing data for streaming operations.
     */
    class ChunkRecord {
        public final long timestampMs;
        public final long size;

        public ChunkRecord(long timestampMs, long size) {
            this.timestampMs = timestampMs;
            this.size = size;
        }
    }

    /**
     * Notify completion of an operation with detailed metrics.
     * 
     * @param threadId     ID of the thread that performed the operation
     * @param channelId    ID of the channel used (for gRPC)
     * @param peer         Peer/backend that served the request
     * @param object       Object name
     * @param latencyMs    Total latency in milliseconds
     * @param bytes        Total bytes processed
     * @param success      Whether the operation succeeded
     * @param errorCode    Error code if failed (empty if success)
     * @param errorMessage Error message if failed (empty if success)
     * @param chunks       Per-chunk timing data (may be empty)
     */
    void notifyCompleted(
            int threadId,
            int channelId,
            String peer,
            String object,
            long latencyMs,
            long bytes,
            boolean success,
            String errorCode,
            String errorMessage,
            List<ChunkRecord> chunks);
}
