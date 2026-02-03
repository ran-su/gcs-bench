package com.google.cloud.benchmark;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.storage.v2.StorageGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * Channel pool that maintains a fixed pool of channels and distributes
 * requests across them in a round-robin fashion.
 * Matches the C++ RoundRobinChannelPool implementation.
 */
public class RoundRobinChannelPool implements StorageStubProvider {
    private final Supplier<ManagedChannel> channelCreator;
    private final BenchmarkParameters parameters;
    private final GoogleCredentials cachedCredentials;
    private final AtomicReferenceArray<ManagedChannel> channels;
    private final AtomicInteger channelRotator = new AtomicInteger(0);

    public RoundRobinChannelPool(Supplier<ManagedChannel> channelCreator, BenchmarkParameters parameters,
            int poolSize) {
        this.channelCreator = channelCreator;
        this.parameters = parameters;
        this.channels = new AtomicReferenceArray<>(Math.max(1, poolSize));
        this.cachedCredentials = loadCredentials();

        for (int i = 0; i < channels.length(); i++) {
            channels.set(i, channelCreator.get());
        }
    }

    @Override
    public StubHolder getStub() {
        // Use unsigned modulo to avoid issues when counter wraps to Integer.MIN_VALUE
        int index = (channelRotator.getAndIncrement() & Integer.MAX_VALUE) % channels.length();
        ManagedChannel channel = channels.get(index);

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
        // Evict and replace channel on critical errors
        if (status.getCode() == Status.Code.CANCELLED ||
                status.getCode() == Status.Code.DEADLINE_EXCEEDED) {

            // Find the channel in the pool
            for (int i = 0; i < channels.length(); i++) {
                if (channels.get(i) == channel) {
                    ManagedChannel newChannel = channelCreator.get();
                    if (channels.compareAndSet(i, channel, newChannel)) {
                        System.err.println("Evicted and replaced channel at index " + i);
                        channel.shutdown();
                    } else {
                        // Another thread already replaced it
                        newChannel.shutdown();
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void shutdown() {
        for (int i = 0; i < channels.length(); i++) {
            ManagedChannel channel = channels.get(i);
            if (channel != null) {
                channel.shutdown();
            }
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
