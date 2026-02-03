#!/bin/bash
#
# GCS Java Benchmark - Streaming Tests (2MB & 4MB)
# Tests read/write operations across grpc, http (gcs-json), and gcs-grpc clients
#
# Usage:
#   ./scripts/benchmark_streaming.sh --bucket=<bucket-name> [--runs=<N>] [--threads=<N>]
#
# Prerequisites:
#   - Objects named "2MB.dat" and "4MB.dat" must exist in the bucket for read tests
#   - Or use --setup to create them first
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAR_PATH="$PROJECT_ROOT/target/gcs-java-bench-1.0-SNAPSHOT.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Defaults
BUCKET=""
RUNS=10
THREADS=1
WARMUPS=2
SETUP_OBJECTS=false
OUTPUT_DIR="$PROJECT_ROOT/benchmark_results"

# Sizes in bytes
SIZE_2MB=$((2 * 1024 * 1024))
SIZE_4MB=$((4 * 1024 * 1024))

# Client types
CLIENTS=("grpc" "http" "gcs-grpc")

print_usage() {
    echo -e "${BLUE}GCS Java Benchmark - Streaming Tests${NC}"
    echo ""
    echo "Usage: $0 --bucket=<bucket-name> [options]"
    echo ""
    echo "Required:"
    echo "  --bucket=<name>     GCS bucket name"
    echo ""
    echo "Options:"
    echo "  --runs=<N>          Number of runs per test (default: 10)"
    echo "  --threads=<N>       Number of threads (default: 1)"
    echo "  --warmups=<N>       Warmup runs (default: 2)"
    echo "  --setup             Create test objects (2MB.dat, 4MB.dat) before running"
    echo "  --output=<dir>      Output directory for results (default: benchmark_results/)"
    echo ""
    echo "Test Matrix:"
    echo "  Sizes:      2MB, 4MB"
    echo "  Operations: read, write"
    echo "  Clients:    grpc, http (gcs-json), gcs-grpc"
    echo ""
    exit 1
}

# Parse arguments
for arg in "$@"; do
    case $arg in
        --bucket=*)
            BUCKET="${arg#*=}"
            ;;
        --runs=*)
            RUNS="${arg#*=}"
            ;;
        --threads=*)
            THREADS="${arg#*=}"
            ;;
        --warmups=*)
            WARMUPS="${arg#*=}"
            ;;
        --setup)
            SETUP_OBJECTS=true
            ;;
        --output=*)
            OUTPUT_DIR="${arg#*=}"
            ;;
        --help|-h)
            print_usage
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            print_usage
            ;;
    esac
done

if [ -z "$BUCKET" ]; then
    echo -e "${RED}Error: --bucket is required${NC}"
    print_usage
fi

# Ensure JAR is built
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${YELLOW}JAR not found. Building...${NC}"
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -q
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$OUTPUT_DIR/streaming_benchmark_${TIMESTAMP}.csv"
DATA_FILE="$OUTPUT_DIR/streaming_data_${TIMESTAMP}.csv"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        GCS Java Benchmark - Streaming Tests (2MB & 4MB)        ║${NC}"
echo -e "${BLUE}╠════════════════════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║${NC} Bucket:    ${GREEN}$BUCKET${NC}"
echo -e "${BLUE}║${NC} Runs:      ${GREEN}$RUNS${NC}"
echo -e "${BLUE}║${NC} Threads:   ${GREEN}$THREADS${NC}"
echo -e "${BLUE}║${NC} Warmups:   ${GREEN}$WARMUPS${NC}"
echo -e "${BLUE}║${NC} Output:    ${GREEN}$OUTPUT_DIR${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Setup test objects if requested
if [ "$SETUP_OBJECTS" = true ]; then
    echo -e "${YELLOW}Setting up test objects...${NC}"
    
    echo -e "  Creating 2MB.dat..."
    java -jar "$JAR_PATH" \
        --bucket="$BUCKET" \
        --object=2MB.dat \
        --operation=write \
        --write_size=$SIZE_2MB \
        --client=grpc \
        --runs=1 2>/dev/null || true
    
    echo -e "  Creating 4MB.dat..."
    java -jar "$JAR_PATH" \
        --bucket="$BUCKET" \
        --object=4MB.dat \
        --operation=write \
        --write_size=$SIZE_4MB \
        --client=grpc \
        --runs=1 2>/dev/null || true
    
    echo -e "${GREEN}✓ Test objects created${NC}"
    echo ""
fi

# Run a single benchmark
run_benchmark() {
    local client="$1"
    local operation="$2"
    local size_name="$3"
    local size_bytes="$4"
    local object_name="$5"
    local tag="${client}_${operation}_${size_name}"
    
    echo -e "${CYAN}▶ ${client} | ${operation} | ${size_name}${NC}"
    
    local args=(
        --bucket="$BUCKET"
        --client="$client"
        --runs="$RUNS"
        --threads="$THREADS"
        --warmups="$WARMUPS"
        --report_file="$REPORT_FILE"
        --data_file="$DATA_FILE"
        --report_tag="$tag"
    )
    
    if [ "$operation" = "read" ]; then
        args+=(--object="$object_name" --operation=read)
    else
        args+=(--object="${object_name}_write_test.dat" --operation=write --write_size="$size_bytes")
    fi
    
    java -jar "$JAR_PATH" "${args[@]}" 2>&1 | grep -E "(Throughput|p50|p95|p99|Operations|Error)" || true
    echo ""
}

# Run all benchmarks
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                    Running Benchmarks                          ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

for client in "${CLIENTS[@]}"; do
    echo -e "${YELLOW}━━━ Client: $client ━━━${NC}"
    echo ""
    
    # 2MB Read
    run_benchmark "$client" "read" "2MB" "$SIZE_2MB" "2MB.dat"
    
    # 2MB Write
    run_benchmark "$client" "write" "2MB" "$SIZE_2MB" "2MB"
    
    # 4MB Read
    run_benchmark "$client" "read" "4MB" "$SIZE_4MB" "4MB.dat"
    
    # 4MB Write
    run_benchmark "$client" "write" "4MB" "$SIZE_4MB" "4MB"
    
    echo ""
done

echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                   Benchmark Complete!                          ║${NC}"
echo -e "${GREEN}╠════════════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║${NC} Summary:    ${BLUE}$REPORT_FILE${NC}"
echo -e "${GREEN}║${NC} Raw Data:   ${BLUE}$DATA_FILE${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
