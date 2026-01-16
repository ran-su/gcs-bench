# GCS Java Benchmark

Java implementation of the [GCS Benchmark](https://github.com/GoogleCloudPlatform/grpc-gcp-cpp/tree/master/e2e-examples/gcs/benchmark).

## Overview

This benchmark measures Google Cloud Storage (GCS) performance using different client libraries and configurations. It serves as a Java port of the official C++ benchmark, enabling performance comparisons across languages and client implementations.

**C++ Reference:** `https://github.com/GoogleCloudPlatform/grpc-gcp-cpp/tree/master/e2e-examples/gcs/benchmark`

---

## Project Status

### ✅ Implemented (90% Parity)

#### Core Features
- ✅ All 3 operations: `read`, `random-read`, `write`
- ✅ All parameter names match C++ exactly
- ✅ Multi-threading support
- ✅ Warmup runs
- ✅ Object name resolution with templates
- ✅ Configurable timeouts
- ✅ Retry logic (`--trying`)

#### Client Support
- ✅ gRPC direct (`--client=grpc`)
- ✅ GCS Java Client library (`--client=http`(gcs-json), `gcs-grpc`)

#### Channel Policies (gRPC)
- ✅ `perthread` - One channel per thread (default)
- ✅ `const` - Single shared channel
- ✅ `percall` - New channel per operation
- ✅ `pool` - Round-robin pool with configurable size
- ✅ Channel eviction on errors (`CANCELLED`, `DEADLINE_EXCEEDED`)
- ✅ On-demand stub creation

#### Metrics & Reporting
- ✅ Full C++ metrics: threadId, channelId, peer, object, errors, chunks
- ✅ CSV export (`--report_file`, `--data_file`)
- ✅ Percentile latencies (p50, p95, p99)
- ✅ Per-operation detailed tracking
- ✅ Throughput calculation

#### Build Systems
- ✅ Bazel support
- ✅ Maven support

### ⏭️ Not Yet Implemented

- ⏭️ CRC32C validation (`--crc32c` flag exists, logic pending)
- ⏭️ Resumable writes (`--resumable` flag exists, logic pending)
- ⏭️ Work stealing (`--steal_work`)
- ⏭️ TD mode (`--td` flag exists, logic pending)
- ⏭️ Advanced channel policies (`bpool`, `spool`)
- ⏭️ Custom host/network configuration
- ⏭️ OpenTelemetry/Prometheus exports
- ⏭️ gRPC admin interface

> **Note:** All unimplemented features show clear warnings when used

---

## Build & Run

### Prerequisites

- **Java 11+**
- **Bazel** (recommended) or **Maven**
- **Google Cloud credentials** (for authenticated tests)

### Build with Bazel

```bash
# Build
bazel build :gcs-java-bench

# Clean build
bazel clean
bazel build :gcs-java-bench
```

### Build with Maven

```bash
# Build JAR
mvn clean package

# Build without tests
mvn clean package -DskipTests
```

### Authentication

```bash
# Use default credentials
gcloud auth application-default login

# Or set service account
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
```

---

## Usage Examples

### Basic Read Test

```bash
bazel run :gcs-java-bench -- \
  --bucket=my-test-bucket \
  --object=10MB.dat \
  --operation=read \
  --runs=100
```

### Multi-threaded with Warmup

```bash
bazel run :gcs-java-bench -- \
  --bucket=my-test-bucket \
  --object=100MB.dat \
  --operation=read \
  --threads=8 \
  --runs=1000 \
  --warmups=50
```

### gRPC with Channel Pool

```bash
bazel run :gcs-java-bench -- \
  --bucket=my-test-bucket \
  --object=file.dat \
  --client=grpc \
  --cpolicy=pool \
  --carg=8 \
  --threads=16 \
  --runs=1000
```

### Write with Retries

```bash
bazel run :gcs-java-bench -- \
  --bucket=my-test-bucket \
  --object=output.dat \
  --operation=write \
  --write_size=10485760 \
  --trying \
  --runs=100
```

### Random Read with Custom Chunk Size

```bash
bazel run :gcs-java-bench -- \
  --bucket=my-test-bucket \
  --object=large-file.dat \
  --operation=random-read \
  --chunk_size=1048576 \
  --runs=500
```

### Export Results to CSV

```bash
bazel run :gcs-java-bench -- \
  --bucket=my-test-bucket \
  --object=file.dat \
  --runs=1000 \
  --report_file=results.csv \
  --data_file=raw_data.csv \
  --report_tag=baseline_test
```

### Run with Maven JAR

```bash
java -jar target/gcs-java-bench-1.0-SNAPSHOT.jar \
  --bucket=my-test-bucket \
  --object=file.dat \
  --runs=100
```

---

## Parameter Reference

All parameters match the C++ benchmark exactly. See the full list:

```bash
bazel run :gcs-java-bench -- --help
```

### Key Parameters

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `--bucket` | string | GCS bucket name | **required** |
| `--object` | string | Object name | **required** |
| `--client` | string | Client type: `grpc`, `http`, `gcs-json`, `gcs-grpc` | `grpc` |
| `--operation` | string | Operation: `read`, `random-read`, `write` | `read` |
| `--runs` | int | Number of operations | `1` |
| `--warmups` | int | Warmup runs (excluded from results) | `0` |
| `--threads` | int | Number of threads | `1` |
| `--cpolicy` | string | Channel policy: `perthread`, `const`, `pool`, `percall` | auto |
| `--carg` | int | Policy parameter (e.g. pool size) | `0` |
| `--trying` | bool | Retry on failures | `false` |
| `--read_limit` | long | Bytes to read (-1 = all) | `-1` |
| `--write_size` | long | Bytes to write | `0` |
| `--chunk_size` | long | Chunk size for random-read/write | `-1` |
| `--report_file` | string | CSV summary output | `""` |
| `--data_file` | string | CSV detailed data output | `""` |
| `--verbose` | bool | Show debug output | `false` |

---

## Output Format

### Console Output

```
Running benchmark with direct gRPC client, operation: read...
Using pool channel policy with 8 channels
Running actual benchmark...

=== Results ===
Operations: 1000
Total bytes: 10485760000
Duration: 45234 ms
Throughput: 220.45 MB/s

Latency percentiles (ms):
  p50: 42.3
  p75: 58.1
  p95: 89.7
  p99: 124.5
```

### Report File (`--report_file`)

CSV format with summary statistics:

```csv
tag,operation,client,cpolicy,threads,runs,total_bytes,duration_ms,throughput_mbps,p50_ms,p95_ms,p99_ms,success_rate
test1,read,grpc,pool,8,1000,10485760000,45234,220.45,42.3,89.7,124.5,100.00
```

### Data File (`--data_file`)

CSV format with per-operation details:

```csv
tag,operation,timestamp_ms,latency_ms,bytes,success
test1,read,1700000001234,42,10485760,true
test1,read,1700000001289,45,10485760,true
```

---

## Comparing with C++

To run equivalent tests in C++ and Java:

**C++:**
```bash
bazel run //e2e-examples/gcs/benchmark:benchmark -- \
  --bucket=test --object=file.dat --runs=1000 --cpolicy=pool --carg=8
```

**Java:**
```bash
bazel run :gcs-java-bench -- \
  --bucket=test --object=file.dat --runs=1000 --cpolicy=pool --carg=8
```

The parameters are **identical** - you can copy-paste command lines between implementations!

---

## Development

### Project Structure

```
gcs-java-bench/
├── BUILD                    # Bazel build config
├── WORKSPACE               # Bazel workspace
├── pom.xml                 # Maven build config
├── README.md               # This file
└── src/main/java/com/google/cloud/benchmark/
    ├── Main.java                      # Entry point
    ├── BenchmarkParameters.java       # CLI parameters
    ├── BenchmarkRunner.java           # Runner interface
    ├── GrpcRunner.java                # gRPC implementation
    ├── GcsRunner.java                 # GCS client implementation
    ├── StorageStubProvider.java       # Channel pool interface
    ├── ConstChannelPool.java          # Const policy
    ├── PerThreadChannelPool.java      # Per-thread policy
    ├── PerCallChannelPool.java        # Per-call policy
    ├── RoundRobinChannelPool.java     # Pool policy
    ├── ChannelFactory.java            # Channel creation
    ├── RunnerWatcher.java             # Metrics interface
    ├── StatWatcher.java               # Metrics implementation
    ├── ReportWriter.java              # CSV export
    ├── ResultPrinter.java             # Console output
    ├── ObjectResolver.java            # Name templating
    └── RandomData.java                # Data generation
```

### Adding New Features

1. Check C++ implementation for reference
2. Add parameter to `BenchmarkParameters.java`
3. Implement logic in appropriate runner
4. Add tests
5. Update this README

---

## Profiling

The benchmark includes async-profiler integration for finding performance bottlenecks in the GCS SDK.

### Quick Start

```bash
# Profile a read operation (wall-clock mode - recommended for I/O)
./scripts/profile.sh wall read_test -- --bucket=my-bucket --object=10MB.dat --runs=100

# Profile CPU-only (ignores I/O wait time)
./scripts/profile.sh cpu cpu_analysis -- --bucket=my-bucket --object=file.dat --runs=100

# Profile lock contention (good for high-concurrency tests)
./scripts/profile.sh lock contention -- --bucket=my-bucket --object=file.dat --threads=32 --runs=500

# Profile memory allocations (GC pressure analysis)
./scripts/profile.sh alloc memory_test -- --bucket=my-bucket --object=file.dat --runs=100

# Run all profiling modes
./scripts/profile.sh all comprehensive -- --bucket=my-bucket --object=file.dat --runs=200
```

### Profile Modes

| Mode | Best For | What It Shows |
|------|----------|---------------|
| `wall` | **I/O-heavy workloads** (recommended) | Where time is actually spent, including I/O waits, locks, network |
| `cpu` | CPU-bound analysis | Computation hotspots only, ignores I/O time |
| `lock` | High-concurrency tests | Where threads block waiting for locks |
| `alloc` | Memory/GC analysis | Where objects are allocated, GC pressure sources |

### Interpreting Results

The profiler outputs interactive HTML flame graphs to `profiles/`. Key areas to examine for GCS SDK optimization:

| Package | What It Represents |
|---------|--------------------|
| `com.google.cloud.storage.*` | GCS Java SDK methods |
| `io.grpc.*` | gRPC transport layer |
| `io.netty.*` | Network I/O operations |
| `com.google.protobuf.*` | Protocol buffer serialization |
| `com.google.auth.*` | Authentication/credential handling |

> **Tip:** The script auto-downloads async-profiler on first run. Profiles are saved with timestamps for easy comparison.

---

## Troubleshooting

### Build Errors

```bash
# Clean and rebuild
bazel clean --expunge
bazel build :gcs-java-bench

# Maven clean
mvn clean package
```

### Authentication Issues

```bash
# Verify credentials
gcloud auth application-default login
gcloud config list

# Test with insecure mode (no auth)
bazel run :gcs-java-bench -- --bucket=test --object=file --cred=insecure
```

### Performance Issues

- Use `--cpolicy=pool --carg=N` for better throughput
- Increase `--threads` to match CPU cores
- Use `--warmups` to exclude JIT warmup from results
- Enable `--verbose` to debug issues

---

## Contributing

This project aims for 100% parity with the C++ benchmark. Priority areas:

1. **High Priority:** CRC32C validation, resumable writes
2. **Medium Priority:** Advanced channel policies (`bpool`, `spool`)
3. **Low Priority:** OpenTelemetry integration, custom network paths

---

## License

Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0
