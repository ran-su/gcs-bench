package com.google.cloud.benchmark;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating gRPC channels with custom configuration.
 */
public class ChannelFactory {

    /**
     * Create a channel with the specified parameters.
     * 
     * @param parameters Benchmark parameters containing channel args
     * @param logArgs    Whether to log applied arguments
     * @return Configured ManagedChannel
     */
    public static ManagedChannel createChannel(BenchmarkParameters parameters, boolean logArgs) {
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress("storage.googleapis.com", 443);

        if (parameters.channelArgs != null && !parameters.channelArgs.isEmpty()) {
            if (logArgs) {
                System.out.println("Applying channel args: " + parameters.channelArgs);
            }

            if (channelBuilder instanceof NettyChannelBuilder) {
                NettyChannelBuilder nettyBuilder = (NettyChannelBuilder) channelBuilder;
                String[] args = parameters.channelArgs.split(",");

                for (String arg : args) {
                    String[] parts = arg.split("=");
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        try {
                            switch (key) {
                                case "grpc.keepalive_time_ms":
                                    nettyBuilder.keepAliveTime(Long.parseLong(value), TimeUnit.MILLISECONDS);
                                    break;
                                case "grpc.keepalive_timeout_ms":
                                    nettyBuilder.keepAliveTimeout(Long.parseLong(value), TimeUnit.MILLISECONDS);
                                    break;
                                case "grpc.keepalive_permit_without_calls":
                                    nettyBuilder.keepAliveWithoutCalls(
                                            Boolean.parseBoolean(value) || "1".equals(value));
                                    break;
                                default:
                                    if (logArgs) {
                                        System.err.println("Unsupported channel arg: " + key);
                                    }
                            }
                        } catch (Exception e) {
                            if (logArgs) {
                                System.err.println("Failed to apply channel arg " + key + ": " + e.getMessage());
                            }
                        }
                    }
                }
            } else {
                if (logArgs) {
                    System.err.println(
                            "WARNING: Channel builder is not NettyChannelBuilder, cannot apply channel args.");
                }
            }
        }

        return channelBuilder.build();
    }
}
