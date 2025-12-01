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
    private final AtomicReferenceArray<ManagedChannel> channels;
    private final AtomicInteger channelRotator = new AtomicInteger(0);

    public RoundRobinChannelPool(Supplier<ManagedChannel> channelCreator, BenchmarkParameters parameters,
            int poolSize) {
        this.channelCreator = channelCreator;
        this.parameters = parameters;
        this.channels = new AtomicReferenceArray<>(Math.max(1, poolSize));

        for (int i = 0; i < channels.length(); i++) {
            channels.set(i, channelCreator.get());
        }
    }

    @Override
    public StubHolder getStub() {
        int index = Math.abs(channelRotator.getAndIncrement() % channels.length());
        ManagedChannel channel = channels.get(index);

        StorageGrpc.StorageBlockingStub blockingStub = StorageGrpc.newBlockingStub(channel);
        StorageGrpc.StorageStub asyncStub = StorageGrpc.newStub(channel);

        try {
            GoogleCredentials creds = getCredentials();
            if (creds != null) {
                blockingStub = blockingStub.withCallCredentials(MoreCallCredentials.from(creds));
                asyncStub = asyncStub.withCallCredentials(MoreCallCredentials.from(creds));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load credentials", e);
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

    private GoogleCredentials getCredentials() throws IOException {
        if ("insecure".equalsIgnoreCase(parameters.cred)) {
            return null;
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
