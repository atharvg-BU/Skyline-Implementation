#!/bin/sh
set -eu

# Updates are forwarded to SkylineApp and applied in argument order.
# Example: ./run-skyline.sh Dataset_for_PA1 --insert new-point 2 3 --delete new-point
if [ "$#" -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    printf 'Usage: %s <dataset-file> [--insert <id> <x> <y> | --delete <id>]...\n' "$0"
    printf 'Dataset point IDs are their original line numbers. Inserted IDs must be unique.\n'
    if [ "$#" -eq 0 ]; then
        exit 1
    fi
    exit 0
fi

SKYLINE_PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/rtree" && pwd)
SKYLINE_JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
SKYLINE_OUTPUT_DIR="$SKYLINE_PROJECT_DIR/target/skyline-classes"

# Use the existing library build and dependencies downloaded by Maven.
SKYLINE_RTREE_JAR="$SKYLINE_PROJECT_DIR/target/rtree-0.13-SNAPSHOT.jar"
SKYLINE_GUAVA_JAR="$HOME/.m2/repository/com/github/davidmoten/guava-mini/0.1.7/guava-mini-0.1.7.jar"
SKYLINE_RXJAVA_JAR="$HOME/.m2/repository/io/reactivex/rxjava/1.3.8/rxjava-1.3.8.jar"

for SKYLINE_REQUIRED_JAR in "$SKYLINE_RTREE_JAR" "$SKYLINE_GUAVA_JAR" "$SKYLINE_RXJAVA_JAR"; do
    if [ ! -f "$SKYLINE_REQUIRED_JAR" ]; then
        printf 'Missing required library: %s\nBuild the R-tree project with Maven first.\n' "$SKYLINE_REQUIRED_JAR" >&2
        exit 1
    fi
done

SKYLINE_CLASSPATH="$SKYLINE_RTREE_JAR:$SKYLINE_GUAVA_JAR:$SKYLINE_RXJAVA_JAR"
mkdir -p "$SKYLINE_OUTPUT_DIR"
"$SKYLINE_JAVA_HOME/bin/javac" -cp "$SKYLINE_CLASSPATH" -d "$SKYLINE_OUTPUT_DIR" \
    "$SKYLINE_PROJECT_DIR"/src/main/java/skyline/*.java
"$SKYLINE_JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$SKYLINE_OUTPUT_DIR:$SKYLINE_CLASSPATH" skyline.SkylineApp "$@"
