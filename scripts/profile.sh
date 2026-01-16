#!/bin/bash
#
# GCS Java Benchmark Profiling Script
# Uses async-profiler for low-overhead profiling of GCS SDK operations
#
# Usage:
#   ./scripts/profile.sh [profile_mode] [output_name] -- [benchmark args]
#
# Examples:
#   ./scripts/profile.sh wall gcs_read -- --bucket=my-bucket --object=file.dat --runs=100
#   ./scripts/profile.sh cpu gcs_write -- --bucket=my-bucket --object=out.dat --operation=write --runs=50
#   ./scripts/profile.sh lock contention_test -- --bucket=my-bucket --object=file.dat --threads=16 --runs=200
#   ./scripts/profile.sh alloc memory_test -- --bucket=my-bucket --object=file.dat --runs=100
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PROFILER_DIR="$PROJECT_ROOT/tools/async-profiler"
OUTPUT_DIR="$PROJECT_ROOT/profiles"
JAR_PATH="$PROJECT_ROOT/target/gcs-java-bench-1.0-SNAPSHOT.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_usage() {
    echo -e "${BLUE}GCS Java Benchmark Profiler${NC}"
    echo ""
    echo "Usage: $0 [profile_mode] [output_name] -- [benchmark args]"
    echo ""
    echo "Profile Modes (choose one):"
    echo "  ${GREEN}wall${NC}    - Wall-clock time (RECOMMENDED for I/O-heavy workloads)"
    echo "            Shows where time is actually spent, including I/O waits, locks, etc."
    echo ""
    echo "  ${GREEN}cpu${NC}     - CPU profiling (shows computation hotspots only)"
    echo "            Best for CPU-bound code, ignores I/O time"
    echo ""
    echo "  ${GREEN}lock${NC}    - Lock contention profiling"
    echo "            Shows where threads are blocked waiting for locks"
    echo ""
    echo "  ${GREEN}alloc${NC}   - Memory allocation profiling"
    echo "            Shows where objects are allocated (GC pressure)"
    echo ""
    echo "  ${GREEN}all${NC}     - Run all profiling modes (generates multiple outputs)"
    echo ""
    echo "Examples:"
    echo "  $0 wall read_test -- --bucket=my-bucket --object=10MB.dat --runs=100"
    echo "  $0 cpu grpc_write -- --bucket=my-bucket --object=out.dat --operation=write --runs=50"
    echo "  $0 lock high_concurrency -- --bucket=my-bucket --object=file.dat --threads=32 --runs=500"
    echo "  $0 all comprehensive -- --bucket=my-bucket --object=file.dat --runs=200"
    echo ""
    echo "Output:"
    echo "  HTML flame graphs will be saved to: $OUTPUT_DIR/"
    echo ""
    exit 1
}

# Check for required arguments
if [ $# -lt 2 ]; then
    print_usage
fi

PROFILE_MODE="$1"
OUTPUT_NAME="$2"
shift 2

# Find the -- separator
while [ $# -gt 0 ] && [ "$1" != "--" ]; do
    shift
done

if [ "$1" = "--" ]; then
    shift
fi

BENCHMARK_ARGS="$@"

if [ -z "$BENCHMARK_ARGS" ]; then
    echo -e "${RED}Error: No benchmark arguments provided after '--'${NC}"
    print_usage
fi

# Validate profile mode
case "$PROFILE_MODE" in
    wall|cpu|lock|alloc|all)
        ;;
    *)
        echo -e "${RED}Error: Invalid profile mode '$PROFILE_MODE'${NC}"
        print_usage
        ;;
esac

# Ensure the JAR is built
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${YELLOW}JAR not found. Building...${NC}"
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -q
fi

# Install async-profiler if not present
install_profiler() {
    if [ ! -d "$PROFILER_DIR" ]; then
        echo -e "${YELLOW}Installing async-profiler...${NC}"
        mkdir -p "$PROFILER_DIR"
        
        # Detect OS and architecture
        OS="$(uname -s)"
        ARCH="$(uname -m)"
        
        if [ "$OS" = "Darwin" ]; then
            PLATFORM="macos"
        elif [ "$OS" = "Linux" ]; then
            PLATFORM="linux"
        else
            echo -e "${RED}Unsupported OS: $OS${NC}"
            exit 1
        fi
        
        if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
            ARCH_SUFFIX="-arm64"
        else
            ARCH_SUFFIX=""
        fi
        
        PROFILER_VERSION="3.0"
        DOWNLOAD_URL="https://github.com/async-profiler/async-profiler/releases/download/v${PROFILER_VERSION}/async-profiler-${PROFILER_VERSION}-${PLATFORM}${ARCH_SUFFIX}.zip"
        
        echo -e "${BLUE}Downloading from: $DOWNLOAD_URL${NC}"
        curl -L "$DOWNLOAD_URL" -o "$PROFILER_DIR/async-profiler.zip"
        unzip -q "$PROFILER_DIR/async-profiler.zip" -d "$PROFILER_DIR"
        rm "$PROFILER_DIR/async-profiler.zip"
        
        # Move contents up one level
        mv "$PROFILER_DIR"/async-profiler-*/* "$PROFILER_DIR/"
        rmdir "$PROFILER_DIR"/async-profiler-*/
        
        echo -e "${GREEN}async-profiler installed successfully${NC}"
    fi
}

install_profiler

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Find the profiler binary
ASPROF="$PROFILER_DIR/bin/asprof"
if [ ! -f "$ASPROF" ]; then
    # Try older naming convention
    ASPROF="$PROFILER_DIR/profiler.sh"
fi

if [ ! -f "$ASPROF" ]; then
    echo -e "${RED}Error: async-profiler binary not found${NC}"
    exit 1
fi

# Function to run a single profiling session
run_profile() {
    local mode="$1"
    local name="$2"
    local event_flag=""
    
    case "$mode" in
        wall)
            event_flag="-e wall"
            ;;
        cpu)
            event_flag="-e cpu"
            ;;
        lock)
            event_flag="-e lock"
            ;;
        alloc)
            event_flag="-e alloc"
            ;;
    esac
    
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    OUTPUT_FILE="$OUTPUT_DIR/${name}_${mode}_${TIMESTAMP}.html"
    
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}Running ${GREEN}${mode}${BLUE} profiling: ${name}${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}Benchmark args:${NC} $BENCHMARK_ARGS"
    echo -e "${YELLOW}Output file:${NC} $OUTPUT_FILE"
    echo ""
    
    # Run the benchmark with profiling
    # Note: Using agentpath for cleaner integration
    AGENT_PATH="$PROFILER_DIR/lib/libasyncProfiler.so"
    if [ ! -f "$AGENT_PATH" ]; then
        # macOS uses .dylib
        AGENT_PATH="$PROFILER_DIR/lib/libasyncProfiler.dylib"
    fi
    
    if [ -f "$AGENT_PATH" ]; then
        # Use Java agent for more accurate profiling
        java -agentpath:"$AGENT_PATH=start,${event_flag#-e },file=$OUTPUT_FILE" \
             -jar "$JAR_PATH" $BENCHMARK_ARGS
    else
        # Fallback to asprof wrapper
        "$ASPROF" $event_flag -o flamegraph -f "$OUTPUT_FILE" \
            java -jar "$JAR_PATH" $BENCHMARK_ARGS
    fi
    
    echo ""
    echo -e "${GREEN}✓ Profile saved to: $OUTPUT_FILE${NC}"
    
    # Try to open in browser (macOS)
    if [ "$(uname -s)" = "Darwin" ]; then
        echo -e "${BLUE}Opening in browser...${NC}"
        open "$OUTPUT_FILE" 2>/dev/null || true
    fi
}

# Run profiling based on mode
if [ "$PROFILE_MODE" = "all" ]; then
    echo -e "${BLUE}Running all profiling modes...${NC}"
    for mode in wall cpu lock alloc; do
        run_profile "$mode" "$OUTPUT_NAME"
        echo ""
    done
    echo -e "${GREEN}All profiles complete! Check $OUTPUT_DIR/${NC}"
else
    run_profile "$PROFILE_MODE" "$OUTPUT_NAME"
fi

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                    Profiling Complete!                        ║${NC}"
echo -e "${GREEN}╠═══════════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║ Results saved to: ${BLUE}$OUTPUT_DIR/${NC}"
echo -e "${GREEN}║                                                               ║${NC}"
echo -e "${GREEN}║ Interpreting the flame graph:                                 ║${NC}"
echo -e "${GREEN}║  • Width = percentage of time/samples                         ║${NC}"
echo -e "${GREEN}║  • Y-axis = call stack depth                                  ║${NC}"
echo -e "${GREEN}║  • Click to zoom, hover for details                           ║${NC}"
echo -e "${GREEN}║                                                               ║${NC}"
echo -e "${GREEN}║ For GCS SDK optimization, look for:                           ║${NC}"
echo -e "${GREEN}║  • com.google.cloud.storage.* - SDK methods                   ║${NC}"
echo -e "${GREEN}║  • io.grpc.* - gRPC transport layer                           ║${NC}"
echo -e "${GREEN}║  • io.netty.* - Network I/O                                   ║${NC}"
echo -e "${GREEN}║  • com.google.protobuf.* - Serialization                      ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════╝${NC}"
