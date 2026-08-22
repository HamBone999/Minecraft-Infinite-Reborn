@echo off
title Infdev+ Server

REM ============================================================
REM  Infdev+ dedicated server launcher
REM
REM  Put this file in the SAME FOLDER as the server jar,
REM  then double-click it to start the server.
REM ============================================================

REM --- settings you might want to change ---------------------
set "JAR=Infdev-Server-fixed.jar"
set "MEMORY=4G"
set "AUTO_RESTART=0"
REM
REM   MEMORY        RAM the server may use. 4G = 4 GB.
REM                 Your PC needs more than this in TOTAL -- leave a couple of
REM                 GB for Windows. Never set it above your physical RAM.
REM
REM   AUTO_RESTART  set to 1 to relaunch automatically when the server stops.
REM                 Off by default on purpose: if the server is crashing, a
REM                 restart loop hides the crash instead of showing it to you.
REM -----------------------------------------------------------

cd /d "%~dp0"

if not exist "%JAR%" goto nojar
where java >nul 2>&1
if errorlevel 1 goto nojava

echo.
echo  ============================================
echo   Infdev+ Server
echo   jar    : %JAR%
echo   memory : %MEMORY%
echo  ============================================
echo.
java -version 2>&1
echo.
echo  Type commands straight into this window - try: help
echo  Shut down with the "stop" command, NOT the X button.
echo  Closing the window can lose recent world changes.
echo.

:start
java -Xms1G -Xmx%MEMORY% -XX:+HeapDumpOnOutOfMemoryError "-XX:HeapDumpPath=%~dp0crash-dump.hprof" -Djava.util.Arrays.useLegacyMergeSort=true -jar "%JAR%" nogui

set "CODE=%ERRORLEVEL%"
echo.
echo  Server stopped. Exit code: %CODE%

if exist "%~dp0crash-dump.hprof" echo.
if exist "%~dp0crash-dump.hprof" echo  *** crash-dump.hprof was written - the server ran OUT OF MEMORY.
if exist "%~dp0crash-dump.hprof" echo  *** Keep that file. It records exactly what filled the heap.

if not "%AUTO_RESTART%"=="1" goto done
echo  Restarting in 5 seconds - close this window to stop.
timeout /t 5 /nobreak >nul
goto start

:nojar
echo.
echo   ERROR: cannot find "%JAR%" in this folder.
echo   This file must sit next to the server jar.
echo.
echo   Jars found here:
dir /b *.jar 2>nul
echo.
echo   If the name differs, edit this file and change the JAR line near the top.
goto done

:nojava
echo.
echo   ERROR: Java was not found on your PATH.
echo.
echo   Either install Java, or edit this file and replace the word java
echo   on the :start line with the full path to java.exe in quotes, e.g.
echo     "C:\Program Files\Java\jre1.8.0_351\bin\java.exe"
goto done

:done
echo.
pause
