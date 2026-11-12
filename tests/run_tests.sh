#!/usr/bin/env bash
# Standalone regression tests for the pure engine (no Android SDK needed).
# Downloads the Kotlin compiler (embeddable) + stdlib from Maven Central
# into tests/.kotlinc/ on first run, compiles the pure sources + the test
# file, and runs the suite. Exits non-zero on any failure.
set -euo pipefail
cd "$(dirname "$0")/.."

KOTLIN_VERSION=2.0.20
CACHE="tests/.kotlinc"
mkdir -p "$CACHE/build"
EMBEDDABLE="$CACHE/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar"
STDLIB="$CACHE/kotlin-stdlib-$KOTLIN_VERSION.jar"
BASE="https://repo1.maven.org/maven2/org/jetbrains/kotlin"

if [ ! -s "$EMBEDDABLE" ]; then
  echo "Downloading kotlin-compiler-embeddable $KOTLIN_VERSION ..."
  curl -fsSL -o "$EMBEDDABLE" "$BASE/kotlin-compiler-embeddable/$KOTLIN_VERSION/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar"
fi
if [ ! -s "$STDLIB" ]; then
  echo "Downloading kotlin-stdlib $KOTLIN_VERSION ..."
  curl -fsSL -o "$STDLIB" "$BASE/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar"
fi
# Transitive deps the embeddable compiler needs on its own classpath.
ANNOTATIONS="$CACHE/annotations-13.0.jar"
COROUTINES="$CACHE/kotlinx-coroutines-core-jvm-1.8.1.jar"
TROVE="$CACHE/trove4j-1.0.20200330.jar"
if [ ! -s "$ANNOTATIONS" ]; then
  curl -fsSL -o "$ANNOTATIONS" "https://repo1.maven.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.jar"
fi
if [ ! -s "$COROUTINES" ]; then
  curl -fsSL -o "$COROUTINES" "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar"
fi
if [ ! -s "$TROVE" ]; then
  curl -fsSL -o "$TROVE" "https://repo1.maven.org/maven2/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar"
fi

SRC="app/src/main/java/com/autoscribe/android"
echo "Compiling pure sources ..."
java -Xmx512m -cp "$EMBEDDABLE:$STDLIB:$ANNOTATIONS:$COROUTINES:$TROVE" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$STDLIB" -d "$CACHE/build" \
  "$SRC/engine/Models.kt" \
  "$SRC/engine/TunnelNaming.kt" \
  "$SRC/engine/PingParser.kt" \
  "$SRC/engine/TestPlan.kt" \
  "$SRC/engine/CsvExport.kt" \
  "$SRC/engine/TestEngine.kt" \
  "$SRC/data/StringsData.kt" \
  "$SRC/data/LocationsData.kt" \
  "$SRC/data/Strings.kt" \
  tests/PureLogicTest.kt \
  tests/EngineTest.kt

echo "Running tests ..."
java -cp "$CACHE/build:$STDLIB" PureLogicTestKt
java -cp "$CACHE/build:$STDLIB" EngineTestKt
