package com.google.cloud.benchmark;

import picocli.CommandLine.Option;

public class BenchmarkParameters {
    @Option(names = "--bucket", description = "The bucket to use for the benchmark", required = true)
    public String bucket;

    @Option(names = "--object", description = "The object to use for the benchmark", required = true)
    public String object;

    @Option(names = "--client", description = "Client (grpc, gcs-json, gcs-grpc)", defaultValue = "grpc")
    public String client;

    @Option(names = "--operation", description = "Operation type (read, random-read, write)", defaultValue = "read")
    public String operation;

    @Option(names = "--runs", description = "The number of times to run the operation", defaultValue = "1")
    public int runs;

    @Option(names = "--warmups", description = "The number of warm-up calls to be excluded for the report", defaultValue = "0")
    public int warmups;

    @Option(names = "--threads", description = "The number of threads running operations", defaultValue = "1")
    public int threads;

    @Option(names = "--object_format", description = "Format string to resolve the object (format: {t}=thread-id, {o}=object-id)", defaultValue = "")
    public String objectFormat;

    @Option(names = "--object_start", description = "An integer number specifying at which position to start", defaultValue = "0")
    public int objectStart;

    @Option(names = "--object_stop", description = "An integer number specifying at which position to end", defaultValue = "0")
    public int objectStop;

    @Option(names = "--chunk_size", description = "Chunk size for random-read and write", defaultValue = "-1")
    public long chunkSize;

    @Option(names = "--read_offset", description = "Read offset for read", defaultValue = "-1")
    public long readOffset;

    @Option(names = "--read_limit", description = "Read limit for read", defaultValue = "-1")
    public long readLimit;

    @Option(names = "--write_size", description = "Write size", defaultValue = "0")
    public long writeSize;

    @Option(names = "--timeout", description = "Timeout for the call in seconds (Default: none)", defaultValue = "0")
    public long timeout;

    @Option(names = "--crc32c", description = "Check CRC32C check for received content")
    public boolean crc32c;

    @Option(names = "--resumable", description = "Use resumable-write for writing")
    public boolean resumable;

    @Option(names = "--trying", description = "Keep trying the same operation if failed")
    public boolean trying;

    @Option(names = "--wait_threads", description = "Wait until all threads are done when any of operations fails")
    public boolean waitThreads;

    @Option(names = "--steal_work", description = "Whether worker threads can steal work from other threads")
    public boolean stealWork;

    @Option(names = "--verbose", description = "Show debug output and progress updates")
    public boolean verbose;

    @Option(names = "--grpc_admin", description = "Port for gRPC Admin", defaultValue = "0")
    public int grpcAdmin;

    @Option(names = "--report_tag", description = "The user-defined tag to be inserted in the report", defaultValue = "")
    public String reportTag;

    @Option(names = "--report_file", description = "The file to append the line for the run", defaultValue = "")
    public String reportFile;

    @Option(names = "--data_file", description = "The data file to dump the all data", defaultValue = "")
    public String dataFile;

    @Option(names = "--prometheus_endpoint", description = "Prometheus exporter endpoint", defaultValue = "")
    public String prometheusEndpoint;

    @Option(names = "--host", description = "Host to reach", defaultValue = "")
    public String host;

    @Option(names = "--target_api_version", description = "Target API version (for Json)", defaultValue = "")
    public String targetApiVersion;

    @Option(names = "--access_token", description = "Access token for auth", defaultValue = "")
    public String accessToken;

    @Option(names = "--network", description = "Network path (default, cfe, dp)", defaultValue = "default")
    public String network;

    @Option(names = "--cred", description = "Credential type (insecure,ssl,alts)", defaultValue = "")
    public String cred;

    @Option(names = "--ssl_cert", description = "Path to the server SSL certification chain file", defaultValue = "")
    public String sslCert;

    @Option(names = "--rr", description = "Use round_robin grpclb policy (otherwise pick_first)")
    public boolean rr;

    @Option(names = "--td", description = "Use Traffic Director")
    public boolean td;

    @Option(names = "--tx_zerocopy", description = "Use TCP TX_ZEROCOPY")
    public boolean txZerocopy;

    @Option(names = "--cpolicy", description = "Channel Policy (perthread, percall, const, pool, bpool, spool). Default: const if TD is true else perthread", defaultValue = "")
    public String cpolicy;

    @Option(names = "--carg", description = "Parameter for cpolicy (e.g. pool uses this as the number of channels)", defaultValue = "0")
    public int carg;

    @Option(names = "--ctest", description = "Test to get a list of peers from grpclb", defaultValue = "0")
    public int ctest;

    @Option(names = "--mtest", description = "Test to get metadata", defaultValue = "0")
    public int mtest;

    @Option(names = "--channel_args", description = "Comma-separated list of gRPC channel arguments (key=value)")
    public String channelArgs;

    @Option(names = "--help", usageHelp = true, description = "display this help message")
    boolean help;
}
