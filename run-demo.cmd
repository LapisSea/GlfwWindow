@echo off
rem Runs the glfw-window state demo. Move, resize, maximize (M), full screen (F11), then close and reopen.
rem Arguments go to the demo: a state file (default glfw-demo-state.json), --capture=out.png to save a frame and quit.
rem   run-demo.cmd
rem   run-demo.cmd C:\temp\state.json
cd /d "%~dp0"
call mvn -q test-compile exec:java -Dgpg.skip=true -Dexec.classpathScope=test -Dexec.mainClass=com.lapissea.glfw.demo.StateDemo "-Dexec.args=%*"
