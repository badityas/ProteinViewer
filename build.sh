#!/usr/bin/env bash
# ─── ProteinViewer Build Script ───────────────────────────────────────────────
# Requires: Java 17+ JDK (javac must be on PATH)
# Usage:    ./build.sh          → compile and run
#           ./build.sh compile  → compile only
#           ./build.sh jar      → create runnable JAR
#           ./build.sh clean    → remove build output

set -e

SRC_DIR="src/main/java"
BUILD_DIR="build/classes"
JAR_NAME="ProteinViewer.jar"
MAIN_CLASS="com.proteinviewer.ProteinViewerApp"

check_java() {
    if ! command -v javac &>/dev/null; then
        echo "❌  javac not found. Please install a Java 17+ JDK."
        echo "    macOS:   brew install openjdk"
        echo "    Ubuntu:  sudo apt install default-jdk"
        echo "    Windows: https://adoptium.net"
        exit 1
    fi
    JAVA_VER=$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)
    if [ "$JAVA_VER" -lt 17 ]; then
        echo "❌  Java 17+ required (found $JAVA_VER). Please upgrade."
        exit 1
    fi
    echo "✅  Java $JAVA_VER compiler found."
}

do_compile() {
    echo "🔨  Compiling sources..."
    mkdir -p "$BUILD_DIR"
    find "$SRC_DIR" -name "*.java" > build/sources.txt
    javac --enable-preview --release 21 \
          -d "$BUILD_DIR" \
          -sourcepath "$SRC_DIR" \
          @build/sources.txt
    echo "✅  Compilation successful."
}

do_jar() {
    do_compile
    echo "📦  Building JAR: $JAR_NAME"
    cat > build/manifest.txt << EOF
Main-Class: $MAIN_CLASS
EOF
    jar cfm "$JAR_NAME" build/manifest.txt -C "$BUILD_DIR" .
    echo "✅  JAR created: $JAR_NAME"
    echo "    Run with:  java --enable-preview -jar $JAR_NAME"
}

do_run() {
    do_compile
    echo "🚀  Launching ProteinViewer..."
    java --enable-preview \
         -cp "$BUILD_DIR" \
         -Dswing.aatext=true \
         -Dawt.useSystemAAFontSettings=on \
         "$MAIN_CLASS"
}

do_clean() {
    rm -rf build/ "$JAR_NAME"
    echo "🧹  Cleaned build output."
}

case "${1:-run}" in
    compile) check_java; do_compile ;;
    jar)     check_java; do_jar ;;
    run)     check_java; do_run ;;
    clean)   do_clean ;;
    *)       echo "Usage: $0 [compile|jar|run|clean]"; exit 1 ;;
esac
