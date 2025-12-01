package com.google.cloud.benchmark;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        BenchmarkParameters parameters = new BenchmarkParameters();
        CommandLine cmd = new CommandLine(parameters);

        try {
            cmd.parseArgs(args);
            if (parameters.help) {
                CommandLine.usage(parameters, System.out);
                return;
            }

            // Warn about unimplemented features
            warnUnimplementedFeatures(parameters);

            StatWatcher watcher = new StatWatcher();
            BenchmarkRunner runner;

            if ("grpc".equalsIgnoreCase(parameters.client)) {
                // Create channel factory
                java.util.function.Supplier<io.grpc.ManagedChannel> channelCreator = () -> ChannelFactory
                        .createChannel(parameters, false);

                // Determine channel policy
                String cpolicy = parameters.cpolicy;
                if (cpolicy == null || cpolicy.isEmpty()) {
                    // Default to perthread if not specified
                    cpolicy = "perthread";
                }

                // Create appropriate storage stub provider
                StorageStubProvider stubProvider;
                switch (cpolicy.toLowerCase()) {
                    case "const":
                        stubProvider = new ConstChannelPool(channelCreator, parameters);
                        System.out.println("Using const channel policy (single shared channel)");
                        break;
                    case "perthread":
                        stubProvider = new PerThreadChannelPool(channelCreator, parameters);
                        System.out.println("Using perthread channel policy");
                        break;
                    case "percall":
                        stubProvider = new PerCallChannelPool(channelCreator, parameters);
                        System.out.println("Using percall channel policy (new channel per call)");
                        break;
                    case "pool":
                        int poolSize = parameters.carg > 0 ? parameters.carg : 1;
                        stubProvider = new RoundRobinChannelPool(channelCreator, parameters, poolSize);
                        System.out.println("Using pool channel policy with " + poolSize + " channels");
                        break;
                    case "bpool":
                    case "spool":
                        System.err.println("WARN: " + cpolicy + " policy not yet implemented, using perthread");
                        stubProvider = new PerThreadChannelPool(channelCreator, parameters);
                        break;
                    default:
                        System.err.println("Unknown cpolicy: " + cpolicy + ", defaulting to perthread");
                        stubProvider = new PerThreadChannelPool(channelCreator, parameters);
                }

                runner = new GrpcRunner(parameters, watcher, stubProvider);
            } else {
                runner = new GcsRunner(parameters, watcher);
            }

            long startTime = System.nanoTime();
            runner.run();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;

            ResultPrinter.printResults(watcher.getLatencies(), watcher.getTotalBytes(), durationMs);

            // Write results to files if specified
            if (!parameters.reportFile.isEmpty()) {
                ReportWriter.writeReport(watcher, parameters, parameters.reportFile,
                        parameters.reportTag, durationMs);
                System.out.println("Report written to: " + parameters.reportFile);
            }

            if (!parameters.dataFile.isEmpty()) {
                ReportWriter.writeData(watcher, parameters, parameters.dataFile, parameters.reportTag);
                System.out.println("Data written to: " + parameters.dataFile);
            }

        } catch (CommandLine.ParameterException e) {
            System.err.println(e.getMessage());
            cmd.usage(System.err);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void warnUnimplementedFeatures(BenchmarkParameters params) {
        if (params.td) {
            System.err.println("WARN: --td not yet implemented");
        }
        if (params.crc32c) {
            System.err.println("WARN: --crc32c not yet implemented (flag accepted but no validation performed)");
        }
        if (params.resumable) {
            System.err.println("WARN: --resumable not yet implemented (using non-resumable writes)");
        }
        if (params.waitThreads) {
            System.err.println("WARN: --wait_threads not yet implemented");
        }
        if (params.stealWork) {
            System.err.println("WARN: --steal_work not yet implemented");
        }
        if (!params.prometheusEndpoint.isEmpty()) {
            System.err.println("WARN: --prometheus_endpoint not yet implemented");
        }
        if (params.ctest > 0) {
            System.err.println("WARN: --ctest not yet implemented");
        }
        if (params.mtest > 0) {
            System.err.println("WARN: --mtest not yet implemented");
        }
        if (params.grpcAdmin > 0) {
            System.err.println("WARN: --grpc_admin not yet implemented");
        }
        if (!params.host.isEmpty()) {
            System.err.println("WARN: --host not yet implemented (using default endpoint)");
        }
        if (!params.accessToken.isEmpty()) {
            System.err.println("WARN: --access_token not yet implemented (using default auth)");
        }
        if (!params.targetApiVersion.isEmpty()) {
            System.err.println("WARN: --target_api_version not yet implemented");
        }
        if (!"default".equals(params.network)) {
            System.err.println("WARN: --network not yet implemented (using default network)");
        }
        if (!"".equals(params.cred) && !"insecure".equalsIgnoreCase(params.cred)) {
            System.err.println("WARN: --cred=" + params.cred + " not fully implemented (only 'insecure' is supported)");
        }
        if (!params.sslCert.isEmpty()) {
            System.err.println("WARN: --ssl_cert not yet implemented");
        }
        if (params.rr) {
            System.err.println("WARN: --rr (round_robin) not yet implemented (using pick_first)");
        }
        if (params.txZerocopy) {
            System.err.println("WARN: --tx_zerocopy not yet implemented");
        }
    }
}
