package com.google.cloud.benchmark;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.storage.v2.StorageGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Channel pool that uses a single constant channel shared across all threads.
 */
public class ConstChannelPool implements StorageStubProvider {
    private final Supplier<ManagedChannel> channelCreator;
    private final BenchmarkParameters parameters;
    private final GoogleCredentials cachedCredentials;
    private ManagedChannel channel;

    public ConstChannelPool(Supplier<ManagedChannel> channelCreator, BenchmarkParameters parameters) {
        this.channelCreator = channelCreator;
        this.parameters = parameters;
        this.channel = channelCreator.get();
        this.cachedCredentials = loadCredentials();
    }

    @Override
    public StubHolder getStub() {
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
        // If the channel failed with CANCELLED, replace it
        if (status.getCode() == Status.Code.CANCELLED) {
            synchronized (this) {
                if (this.channel == channel) {
                    System.err.println("Evicted const channel due to CANCELLED status");
                    ManagedChannel newChannel = channelCreator.get();
                    this.channel.shutdown();
                    this.channel = newChannel;
                }
            }
        }
    }

    @Override
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
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
