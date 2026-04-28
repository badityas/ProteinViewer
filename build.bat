@echo off
REM ─── ProteinViewer Build Script (Windows) ────────────────────────────────────
REM Requires: Java 17+ JDK (javac must be on PATH)
REM Usage:    build.bat          → compile and run
REM           build.bat compile  → compile only
REM           build.bat jar      → create runnable JAR
REM           build.bat clean    → remove build output

set SRC_DIR=src\main\java
set BUILD_DIR=build\classes
set JAR_NAME=ProteinViewer.jar
set MAIN_CLASS=com.proteinviewer.ProteinViewerApp

if "%1"=="clean" goto :clean
if "%1"=="jar"   goto :jar
if "%1"=="compile" goto :compile
goto :run

:check_java
javac -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] javac not found. Please install Java 17+ JDK and add it to PATH.
    echo         Download: https://adoptium.net
    exit /b 1
)
echo [OK] Java compiler found.
goto :eof

:compile
call :check_java
echo [BUILD] Compiling sources...
if not exist %BUILD_DIR% mkdir %BUILD_DIR%
dir /s /b %SRC_DIR%\*.java > build\sources.txt
javac --enable-preview --release 21 -d %BUILD_DIR% -sourcepath %SRC_DIR% @build\sources.txt
if errorlevel 1 (echo [ERROR] Compilation failed. & exit /b 1)
echo [OK] Compilation successful.
goto :eof

:jar
call :compile
echo [BUILD] Creating JAR: %JAR_NAME%
echo Main-Class: %MAIN_CLASS% > build\manifest.txt
jar cfm %JAR_NAME% build\manifest.txt -C %BUILD_DIR% .
echo [OK] JAR created: %JAR_NAME%
echo     Run with: java --enable-preview -jar %JAR_NAME%
goto :eof

:run
call :compile
echo [LAUNCH] Starting ProteinViewer...
java --enable-preview -cp %BUILD_DIR% -Dswing.aatext=true -Dawt.useSystemAAFontSettings=on %MAIN_CLASS%
goto :eof

:clean
echo [CLEAN] Removing build output...
if exist build rmdir /s /q build
if exist %JAR_NAME% del %JAR_NAME%
echo [OK] Cleaned.
goto :eof
