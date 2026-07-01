#!/bin/bash
# Environment setup script for Linux/macOS
# Sets JAVA_HOME to JAVA_HOME_8 for JDK 8 compatibility
# Run as: ./mvn-env.sh <maven-args>

set -e

# Check if JAVA_HOME_8 is set
if [[ -z "${JAVA_HOME_8}" ]]; then
    echo "Error: JAVA_HOME_8 environment variable is not set."
    echo "Please set JAVA_HOME_8 to the path of your JDK 8 installation."
    echo "Example: export JAVA_HOME_8=/usr/lib/jvm/java-1.8.0-amazon-corretto"
    exit 1
fi

# Verify the JDK 8 path exists
if [[ ! -d "${JAVA_HOME_8}" ]]; then
    echo "Error: JDK 8 not found at ${JAVA_HOME_8}"
    echo "Please ensure JAVA_HOME_8 points to a valid JDK 8 installation."
    exit 1
fi

# Set JAVA_HOME
export JAVA_HOME="${JAVA_HOME_8}"

echo "Using JDK 8 at ${JAVA_HOME}"

# Get the absolute path of the script directory
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TMP_DIR="$SCRIPT_DIR/tmp"

mkdir -p "$TMP_DIR"

# Run Maven wrapper with all arguments
exec "$SCRIPT_DIR/mvnw" -Djava.io.tmpdir="$TMP_DIR" "$@"

