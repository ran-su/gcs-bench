package com.google.cloud.benchmark;

import com.google.protobuf.ByteString;
import com.google.storage.v2.ChecksummedData;
import com.google.storage.v2.Object;
import com.google.storage.v2.ReadObjectRequest;
import com.google.storage.v2.ReadObjectResponse;
import com.google.storage.v2.WriteObjectRequest;
import com.google.storage.v2.WriteObjectResponse;
import com.google.storage.v2.WriteObjectSpec;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-based benchmark runner using StorageStubProvider for channel management.
 * Supports multiple channel pooling policies.
 */
public class GrpcRunner implements BenchmarkRunner {
    private final BenchmarkParameters parameters;
    private final RunnerWatcher watcher;
    private final StorageStubProvider stubProvider;
    private byte[] sharedRandomData;

    public GrpcRunner(BenchmarkParameters parameters, RunnerWatcher watcher, StorageStubProvider stubProvider) {
        this.parameters = parameters;
        this.watcher = watcher;
        this.stubProvider = stubProvider;

        if ("write".equalsIgnoreCase(parameters.operation) && parameters.writeSize > 0
                && parameters.writeSize <= 256 * 1024 * 1024) {
            this.sharedRandomData = RandomData.generate((int) parameters.writeSize);
        }
    }

    @Override
    public void run() {
        System.out.printf("Running benchmark with direct gRPC client, operation: %s...%n", parameters.operation);

        if (parameters.warmups > 0) {
            System.out.println("Running warmup...");
            runOperations(parameters.warmups, parameters.threads, null);
        }

        System.out.println("Running actual benchmark...");
        runOperations(parameters.runs, parameters.threads, watcher);

        stubProvider.shutdown();
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

                    // Retry loop (matches C++ implementation)
                    while (true) {
                        try {
                            if ("write".equalsIgnoreCase(parameters.operation)) {
                                bytesProcessed = performWrite(objectName);
                            } else if ("random-read".equalsIgnoreCase(parameters.operation)) {
                                bytesProcessed = performRandomRead(objectName);
                            } else {
                                bytesProcessed = performRead(objectName);
                            }
                            success = true;
                            break; // Success - exit retry loop
                        } catch (Exception e) {
                            if (!parameters.trying) {
                                // Not retrying - rethrow exception
                                throw e;
                            }
                            // Log and retry
                            if (parameters.verbose) {
                                System.err.println("Operation failed, retrying: " + e.getMessage());
                            }
                        }
                    }
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
                    // Note: channelId and peer extraction from gRPC context requires additional
                    // infrastructure
                    // Note: chunk-level timing requires instrumenting the streaming response
                    // iterator
                    currentWatcher.notifyCompleted(
                            threadId,
                            0, // channelId - requires tracking in StorageStubProvider
                            "", // peer - requires gRPC ClientInterceptor to extract
                            objectName,
                            (long) ((end - start) / 1_000_000.0),
                            bytesProcessed,
                            success,
                            errorCode,
                            errorMessage,
                            new ArrayList<>() // chunks - requires wrapping response iterator
                    );
                }
            });
        }

        executor.shutdown();
        try {
            if (parameters.timeout > 0) {
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

    private long performRead(String objectName) {
        StorageStubProvider.StubHolder holder = stubProvider.getStub();
        String bucketName = "projects/_/buckets/" + parameters.bucket;

        ReadObjectRequest.Builder reqBuilder = ReadObjectRequest.newBuilder()
                .setBucket(bucketName)
                .setObject(objectName);

        long offset = parameters.readOffset > 0 ? parameters.readOffset : 0;
        if (offset > 0) {
            reqBuilder.setReadOffset(offset);
        }
        if (parameters.readLimit > 0) {
            reqBuilder.setReadLimit(parameters.readLimit);
        }

        try {
            Iterator<ReadObjectResponse> iterator = holder.blockingStub.readObject(reqBuilder.build());
            long totalBytes = 0;
            while (iterator.hasNext()) {
                ReadObjectResponse response = iterator.next();
                if (response.hasChecksummedData()) {
                    totalBytes += response.getChecksummedData().getContent().size();
                }
            }
            stubProvider.reportResult(holder.channel, Status.OK, totalBytes);
            return totalBytes;
        } catch (io.grpc.StatusRuntimeException e) {
            stubProvider.reportResult(holder.channel, e.getStatus(), 0);
            throw e;
        }
    }

    private long performWrite(String objectName) throws InterruptedException {
        StorageStubProvider.StubHolder holder = stubProvider.getStub();
        String bucketName = "projects/_/buckets/" + parameters.bucket;

        long size = parameters.writeSize > 0 ? parameters.writeSize : 1024 * 1024;
        byte[] data;
        if (this.sharedRandomData != null && this.sharedRandomData.length == size) {
            data = this.sharedRandomData;
        } else {
            data = RandomData.generate((int) size);
        }

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final long[] resultSize = { 0 };
        final Throwable[] error = { null };

        io.grpc.stub.StreamObserver<WriteObjectRequest> requestObserver = holder.asyncStub
                .writeObject(new io.grpc.stub.StreamObserver<WriteObjectResponse>() {
                    @Override
                    public void onNext(WriteObjectResponse value) {
                        if (value.hasResource()) {
                            resultSize[0] = value.getResource().getSize();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        error[0] = t;
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        // Send first message with spec
        WriteObjectSpec spec = WriteObjectSpec.newBuilder()
                .setResource(Object.newBuilder().setBucket(bucketName).setName(objectName).build())
                .build();
        requestObserver.onNext(WriteObjectRequest.newBuilder().setWriteObjectSpec(spec).build());

        // Send data
        int offset = 0;
        int chunkSize = 2 * 1024 * 1024; // 2MB chunks
        while (offset < data.length) {
            int length = Math.min(chunkSize, data.length - offset);
            ByteString content = ByteString.copyFrom(data, offset, length);
            requestObserver.onNext(WriteObjectRequest.newBuilder()
                    .setChecksummedData(ChecksummedData.newBuilder().setContent(content).build())
                    .build());
            offset += length;
        }

        // Finish
        requestObserver.onNext(WriteObjectRequest.newBuilder().setFinishWrite(true).build());
        requestObserver.onCompleted();

        latch.await();
        if (error[0] != null) {
            if (error[0] instanceof io.grpc.StatusRuntimeException) {
                io.grpc.StatusRuntimeException e = (io.grpc.StatusRuntimeException) error[0];
                stubProvider.reportResult(holder.channel, e.getStatus(), size);
            } else {
                stubProvider.reportResult(holder.channel, Status.UNKNOWN, size);
            }
            throw new RuntimeException("Write failed", error[0]);
        }
        stubProvider.reportResult(holder.channel, Status.OK, size);
        return size;
    }

    private long performRandomRead(String objectName) {
        StorageStubProvider.StubHolder holder = stubProvider.getStub();
        String bucketName = "projects/_/buckets/" + parameters.bucket;

        try {
            // Get object metadata to find size
            com.google.storage.v2.GetObjectRequest getReq = com.google.storage.v2.GetObjectRequest.newBuilder()
                    .setBucket(bucketName)
                    .setObject(objectName)
                    .build();
            Object obj = holder.blockingStub.getObject(getReq);
            long objectSize = obj.getSize();

            long chunkSize = parameters.chunkSize > 0 ? parameters.chunkSize : 1024 * 1024;
            long offset = ThreadLocalRandom.current().nextLong(0, Math.max(1, objectSize - chunkSize));

            ReadObjectRequest req = ReadObjectRequest.newBuilder()
                    .setBucket(bucketName)
                    .setObject(objectName)
                    .setReadOffset(offset)
                    .setReadLimit(chunkSize)
                    .build();

            Iterator<ReadObjectResponse> iterator = holder.blockingStub.readObject(req);
            long totalBytes = 0;
            while (iterator.hasNext()) {
                ReadObjectResponse response = iterator.next();
                if (response.hasChecksummedData()) {
                    totalBytes += response.getChecksummedData().getContent().size();
                }
            }
            stubProvider.reportResult(holder.channel, Status.OK, totalBytes);
            return totalBytes;
        } catch (io.grpc.StatusRuntimeException e) {
            stubProvider.reportResult(holder.channel, e.getStatus(), 0);
            throw e;
        }
    }
}
