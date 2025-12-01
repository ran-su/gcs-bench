package com.google.cloud.benchmark;

import com.google.storage.v2.StorageGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;

/**
 * Interface for providing Storage stubs with different channel pooling
 * strategies.
 * Matches the C++ StorageStubProvider pattern.
 */
public interface StorageStubProvider {

    /**
     * Holds the stubs and channel for a single operation.
     */
    class StubHolder {
        public final StorageGrpc.StorageBlockingStub blockingStub;
        public final StorageGrpc.StorageStub asyncStub;
        public final ManagedChannel channel;

        public StubHolder(
                StorageGrpc.StorageBlockingStub blockingStub,
                StorageGrpc.StorageStub asyncStub,
                ManagedChannel channel) {
            this.blockingStub = blockingStub;
            this.asyncStub = asyncStub;
            this.channel = channel;
        }
    }

    /**
     * Get a stub holder for performing an operation.
     * 
     * @return StubHolder containing the stubs and channel
     */
    StubHolder getStub();

    /**
     * Report the result of an operation for metrics and channel management.
     * 
     * @param channel The channel used for the operation
     * @param status  The gRPC status code
     * @param bytes   Number of bytes processed
     */
    void reportResult(ManagedChannel channel, Status status, long bytes);

    /**
     * Shutdown all channels managed by this provider.
     */
    void shutdown();
}
