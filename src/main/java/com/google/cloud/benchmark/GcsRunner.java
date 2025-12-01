package com.google.cloud.benchmark;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class GcsRunner implements BenchmarkRunner {
    private final BenchmarkParameters parameters;
    private final Storage storage;

    private byte[] sharedRandomData;

    private final RunnerWatcher watcher;

    public GcsRunner(BenchmarkParameters parameters, RunnerWatcher watcher) {
        this.parameters = parameters;
        this.watcher = watcher;
        boolean useGrpc = "gcs-grpc".equalsIgnoreCase(parameters.client);

        StorageOptions.Builder builder;
        if (useGrpc) {
            builder = StorageOptions.grpc();
        } else {
            builder = StorageOptions.http();
        }

        if ("insecure".equalsIgnoreCase(parameters.cred)) {
            builder.setCredentials(com.google.cloud.NoCredentials.getInstance());
        }

        this.storage = builder.build().getService();

        // Pre-generate data for writes if size is reasonable to avoid runtime overhead
        if ("write".equalsIgnoreCase(parameters.operation) && parameters.writeSize > 0
                && parameters.writeSize <= 256 * 1024 * 1024) {
            this.sharedRandomData = RandomData.generate((int) parameters.writeSize);
        }
    }

    @Override
    public void run() {
        System.out.printf("Running benchmark with %s client, operation: %s...%n", parameters.client,
                parameters.operation);

        // Warmup
        if (parameters.warmups > 0) {
            System.out.println("Running warmup...");
            // Run warmup iterations (use same thread count as actual run)
            // For warmup, we can pass a dummy watcher or null if we don't want to track
            // stats,
            // or just ignore the result.
            runOperations(parameters.warmups, parameters.threads, null);
        }

        System.out.println("Running actual benchmark...");
        runOperations(parameters.runs, parameters.threads, watcher);
    }

    private void runOperations(int runs, int threads, RunnerWatcher currentWatcher) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < runs; i++) {
            final int threadId = i % threads;
            executor.submit(() -> {
                long start = System.nanoTime();
                long bytesProcessed = 0;
                boolean success = false;
                String objectName = parameters.object;
                String errorCode = "";
                String errorMessage = "";

                try {
                    // Determine object name
                    if (parameters.objectFormat != null && !parameters.objectFormat.isEmpty()) {
                        int objectId = parameters.objectStart;
                        if (parameters.objectStop > parameters.objectStart) {
                            objectId = ThreadLocalRandom.current().nextInt(parameters.objectStart,
                                    parameters.objectStop);
                        }
                        objectName = ObjectResolver.resolveName(parameters.objectFormat, parameters.object, threadId,
                                objectId);
                    }

                    if (parameters.verbose) {
                        System.out.println("Thread " + threadId + " operating on " + objectName);
                    }

                    // Enforce timeout if specified
                    if (parameters.timeout > 0) {
                        bytesProcessed = performOperation(objectName);
                    } else {
                        bytesProcessed = performOperation(objectName);
                    }
                    success = true;
                } catch (Exception e) {
                    if (parameters.verbose) {
                        e.printStackTrace();
                    }
                    success = false;
                    errorCode = e.getClass().getSimpleName();
                    errorMessage = e.getMessage() != null ? e.getMessage() : "";
                }
                long end = System.nanoTime();
                if (currentWatcher != null) {
                    // Call with detailed metrics
                    currentWatcher.notifyCompleted(
                            threadId,
                            0, // channelId - not applicable for GCS client library
                            "", // peer - not available from client library
                            objectName,
                            (long) ((end - start) / 1_000_000.0),
                            bytesProcessed,
                            success,
                            errorCode,
                            errorMessage,
                            new java.util.ArrayList<>() // chunks - client library doesn't expose chunk info
                    );
                }
            });
        }

        executor.shutdown();
        try {
            if (parameters.timeout > 0) {
                // Wait for timeout + buffer
                if (!executor.awaitTermination(parameters.timeout * runs + 60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } else {
                executor.awaitTermination(1, TimeUnit.HOURS);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private long performOperation(String objectName) {
        if ("write".equalsIgnoreCase(parameters.operation)) {
            return performWrite(objectName);
        } else if ("random-read".equalsIgnoreCase(parameters.operation)) {
            return performRandomRead(objectName);
        } else {
            return performRead(objectName);
        }
    }

    private long performRead(String objectName) {
        Blob blob = storage.get(BlobId.of(parameters.bucket, objectName));
        if (blob == null) {
            if (parameters.verbose)
                System.err.println("Object not found: " + objectName);
            return 0;
        }

        long offset = parameters.readOffset > 0 ? parameters.readOffset : 0;

        if (parameters.readLimit > 0 || offset > 0) {
            try (var reader = blob.reader()) {
                if (offset > 0) {
                    reader.seek(offset);
                }

                // If readLimit is specified, read that many bytes.
                // Otherwise read the entire object.
                if (parameters.readLimit > 0) {
                    byte[] buffer = new byte[(int) parameters.readLimit];
                    return reader.read(ByteBuffer.wrap(buffer));
                } else {
                    // Read until EOF
                    ByteBuffer buffer = ByteBuffer.allocate(64 * 1024); // 64KB buffer
                    long totalRead = 0;
                    while (reader.read(buffer) != -1) {
                        totalRead += buffer.position();
                        buffer.clear();
                    }
                    return totalRead;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            byte[] content = blob.getContent();
            return content.length;
        }
    }

    private long performWrite(String objectName) {
        long size = parameters.writeSize > 0 ? parameters.writeSize : 1024 * 1024;
        byte[] data;
        if (this.sharedRandomData != null && this.sharedRandomData.length == size) {
            data = this.sharedRandomData;
        } else {
            data = RandomData.generate((int) size);
        }
        BlobId blobId = BlobId.of(parameters.bucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, data);
        return size;
    }

    private long performRandomRead(String objectName) {
        Blob blob = storage.get(BlobId.of(parameters.bucket, objectName));
        if (blob == null)
            return 0;
        long objectSize = blob.getSize();
        long chunkSize = parameters.chunkSize > 0 ? parameters.chunkSize : 1024 * 1024;

        long offset = ThreadLocalRandom.current().nextLong(0, Math.max(1, objectSize - chunkSize));
        if (offset < 0)
            offset = 0;

        try (var reader = blob.reader()) {
            reader.seek(offset);
            byte[] buffer = new byte[(int) chunkSize];
            return reader.read(ByteBuffer.wrap(buffer));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
