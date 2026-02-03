package com.google.cloud.benchmark;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.storage.v2.StorageGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Channel pool that creates a new channel for every single call.
 * This is useful for testing channel creation overhead.
 */
public class PerCallChannelPool implements StorageStubProvider {
    private final Supplier<ManagedChannel> channelCreator;
    private final BenchmarkParameters parameters;
    private final GoogleCredentials cachedCredentials;

    public PerCallChannelPool(Supplier<ManagedChannel> channelCreator, BenchmarkParameters parameters) {
        this.channelCreator = channelCreator;
        this.parameters = parameters;
        this.cachedCredentials = loadCredentials();
    }

    @Override
    public StubHolder getStub() {
        ManagedChannel channel = channelCreator.get();
        StorageGrpc.StorageBlockingStub blockingStub = StorageGrpc.newBlockingStub(channel);
        StorageGrpc.StorageStub asyncStub = StorageGrpc.newStub(channel);

        if (cachedCredentials != null) {
            blockingStub = blockingStub.withCallCredentials(MoreCallCredentials.from(cachedCredentials));
            asyncStub = asyncStub.withCallCredentials(MoreCallCredentials.from(cachedCredentials));
        }

        return new StubHolder(blockingStub, asyncStub, channel);
    }

    @Override
    public void reportResult(ManagedChannel channel, Status status, long bytes) {
        // Shutdown the channel after each call since we create new ones
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    public void shutdown() {
        // Nothing to shutdown - channels are already closed after each call
    }

    private GoogleCredentials loadCredentials() {
        if ("insecure".equalsIgnoreCase(parameters.cred)) {
            return null;
        }
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load credentials", e);
        }
    }
}
