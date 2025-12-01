package com.google.cloud.benchmark;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for writing benchmark results to files.
 */
public class ReportWriter {

    /**
     * Write a summary report line to a file (appending).
     * Format:
     * tag,operation,threads,runs,total_bytes,duration_ms,throughput_mbps,p50_ms,p95_ms,p99_ms,success_rate
     * 
     * @param watcher    The StatWatcher containing results
     * @param params     Benchmark parameters
     * @param file       Output file path
     * @param tag        User-defined tag for this run
     * @param durationMs Total duration in milliseconds
     */
    public static void writeReport(StatWatcher watcher, BenchmarkParameters params,
            String file, String tag, long durationMs) {
        if (file == null || file.isEmpty()) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
            List<Double> latencies = watcher.getLatencies();
            long totalBytes = watcher.getTotalBytes();
            List<StatWatcher.OperationRecord> ops = watcher.getOperations();

            // Calculate statistics
            double throughputMbps = totalBytes / 1024.0 / 1024.0 / (durationMs / 1000.0);
            double p50 = calculatePercentile(latencies, 50);
            double p95 = calculatePercentile(latencies, 95);
            double p99 = calculatePercentile(latencies, 99);
            double successRate = ops.isEmpty() ? 0.0
                    : (double) ops.stream().filter(op -> op.success).count() / ops.size() * 100.0;

            // Write header if file doesn't exist or is empty
            java.io.File f = new java.io.File(file);
            if (f.length() == 0) {
                writer.println("tag,operation,client,cpolicy,threads,runs,total_bytes,duration_ms," +
                        "throughput_mbps,p50_ms,p95_ms,p99_ms,success_rate");
            }

            // Write data line
            writer.printf("%s,%s,%s,%s,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    tag.isEmpty() ? "default" : tag,
                    params.operation,
                    params.client,
                    params.cpolicy == null || params.cpolicy.isEmpty() ? "auto" : params.cpolicy,
                    params.threads,
                    params.runs,
                    totalBytes,
                    durationMs,
                    throughputMbps,
                    p50, p95, p99,
                    successRate);

        } catch (IOException e) {
            System.err.println("Failed to write report to " + file + ": " + e.getMessage());
        }
    }

    /**
     * Write all operation data to a file.
     * Format: tag,operation,timestamp_ms,latency_ms,bytes,success
     * 
     * @param watcher The StatWatcher containing results
     * @param params  Benchmark parameters
     * @param file    Output file path
     * @param tag     User-defined tag for this run
     */
    public static void writeData(StatWatcher watcher, BenchmarkParameters params, String file, String tag) {
        if (file == null || file.isEmpty()) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
            List<StatWatcher.OperationRecord> ops = watcher.getOperations();

            // Write header if file doesn't exist or is empty
            java.io.File f = new java.io.File(file);
            if (f.length() == 0) {
                writer.println("tag,operation,timestamp_ms,latency_ms,bytes,success");
            }

            // Write each operation
            for (StatWatcher.OperationRecord op : ops) {
                writer.printf("%s,%s,%d,%d,%d,%s%n",
                        tag.isEmpty() ? "default" : tag,
                        params.operation,
                        op.timestampMs,
                        op.latencyMs,
                        op.bytes,
                        op.success ? "true" : "false");
            }

        } catch (IOException e) {
            System.err.println("Failed to write data to " + file + ": " + e.getMessage());
        }
    }

    private static double calculatePercentile(List<Double> values, int percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));

        return sorted.get(index);
    }
}
