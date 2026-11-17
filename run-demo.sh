#!/usr/bin/env sh
# Runs the glfw-window state demo. Move, resize, maximize (M), full screen (F11), then close and reopen.
# Arguments go to the demo: a state file (default glfw-demo-state.json), x11 or wayland to pick the platform.
#   ./run-demo.sh
#   ./run-demo.sh x11
#   ./run-demo.sh wayland /tmp/wayland-state.json
# JAVA_OPTS go to the demo JVM, for example a GLFW build that allows software rendering on a Mac without GPU:
#   JAVA_OPTS=-Dorg.lwjgl.glfw.libname=$HOME/swgl/libglfw.dylib ./run-demo.sh
cd "$(dirname "$0")" || exit 1
# A separate JVM, macOS only runs windows on the process main thread.
JVM_ARGS="${JAVA_OPTS:-}"
[ "$(uname)" = "Darwin" ] && JVM_ARGS="-XstartOnFirstThread $JVM_ARGS"
exec mvn -q test-compile exec:exec -Dgpg.skip=true -Dexec.classpathScope=test -Dexec.executable=java \
	-Dexec.args="$JVM_ARGS -cp %classpath com.lapissea.glfw.demo.StateDemo $*"
