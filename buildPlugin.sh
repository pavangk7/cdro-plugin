#!/usr/bin/env bash
set -e

PLUGIN_NAME="EC-DelineaSecretServer"
PLUGIN_VERSION="1.0.0"
OUTPUT_DIR="build/plugins"
JAR_NAME="${PLUGIN_NAME}-${PLUGIN_VERSION}.jar"

echo "=========================================================="
echo " Building CloudBees CD/RO Plugin: ${PLUGIN_NAME}"
echo "=========================================================="

mkdir -p "${OUTPUT_DIR}"

# Auto-detect modern JDK if required by Gradle
if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
    if [ -d "/opt/homebrew/opt/openjdk@21" ]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
    elif [ -d "/opt/homebrew/opt/openjdk" ]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk"
    fi
fi

# Detect build tool: Maven or Gradle
if [ "$1" = "--maven" ] || command -v mvn >/dev/null 2>&1; then
    echo "Building using Maven (mvn clean package)..."
    mvn clean package -DskipTests
elif [ -f "./mvnw" ]; then
    echo "Building using Maven Wrapper (./mvnw clean package)..."
    ./mvnw clean package -DskipTests
elif command -v gradle >/dev/null 2>&1; then
    echo "Building using Gradle..."
    gradle packagePlugin
elif [ -f "./gradlew" ]; then
    echo "Building using Gradle Wrapper..."
    ./gradlew packagePlugin
else
    echo "Building plugin archive using JAR utility..."
    jar cfm "${OUTPUT_DIR}/${JAR_NAME}" META-INF/MANIFEST.MF config dsl forms src

    # Also create a non-versioned link/file for easy ectool reference
    cp "${OUTPUT_DIR}/${JAR_NAME}" "${OUTPUT_DIR}/${PLUGIN_NAME}.jar"

    echo "Successfully generated plugin archive:"
    echo "  ${OUTPUT_DIR}/${JAR_NAME}"
    echo "  ${OUTPUT_DIR}/${PLUGIN_NAME}.jar"
fi

echo ""
echo "Installation Instructions for CloudBees CD/RO:"
echo "1. Upload via Web UI: Administration -> Plugins -> Install from File -> Select ${OUTPUT_DIR}/${JAR_NAME}"
echo "2. Or install via ectool CLI:"
echo "   ectool login admin <password>"
echo "   ectool installPlugin $(pwd)/${OUTPUT_DIR}/${JAR_NAME}"
echo "   ectool promotePlugin ${PLUGIN_NAME}-${PLUGIN_VERSION}"
echo "=========================================================="
