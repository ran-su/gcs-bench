package com.google.cloud.benchmark;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

public class ResultPrinter {
    public static void printResults(List<Double> latenciesMs, long totalBytes, long totalTimeMs) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (Double latency : latenciesMs) {
            stats.addValue(latency);
        }

        double throughput = (double) totalBytes / 1024 / 1024 / (totalTimeMs / 1000.0);

        System.out.println("Benchmark Results:");
        System.out.printf("Total Time: %d ms%n", totalTimeMs);
        System.out.printf("Total Bytes: %d%n", totalBytes);
        System.out.printf("Throughput: %.2f MiB/s%n", throughput);
        System.out.printf("Latency (ms):%n");
        System.out.printf("  Min: %.2f%n", stats.getMin());
        System.out.printf("  p50: %.2f%n", stats.getPercentile(50));
        System.out.printf("  p90: %.2f%n", stats.getPercentile(90));
        System.out.printf("  p99: %.2f%n", stats.getPercentile(99));
        System.out.printf("  Max: %.2f%n", stats.getMax());
    }
}
