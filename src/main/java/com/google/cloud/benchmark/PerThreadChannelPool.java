package com.google.cloud.benchmark;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.storage.v2.StorageGrpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;

/**
 * Channel pool that creates one channel per thread using ThreadLocal.
 */
public class PerThreadChannelPool implements StorageStubProvider {
    private final BenchmarkParameters parameters;
    private final ThreadLocal<ManagedChannel> threadChannel;
    private final ConcurrentHashMap<Long, ManagedChannel> channels = new ConcurrentHashMap<>();

    public PerThreadChannelPool(Supplier<ManagedChannel> channelCreator, BenchmarkParameters parameters) {
        this.parameters = parameters;
        this.threadChannel = ThreadLocal.withInitial(() -> {
            ManagedChannel channel = channelCreator.get();
            channels.put(Thread.currentThread().getId(), channel);
            return channel;
        });
    }

    @Override
    public StubHolder getStub() {
        ManagedChannel channel = threadChannel.get();
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
        // No action needed for per-thread policy
        // Each thread manages its own channel
    }

    @Override
    public void shutdown() {
        for (ManagedChannel channel : channels.values()) {
            if (channel != null) {
                channel.shutdown();
            }
        }
        channels.clear();
    }

    private GoogleCredentials getCredentials() throws IOException {
        if ("insecure".equalsIgnoreCase(parameters.cred)) {
            return null;
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
