@echo off
rem ------------------------------------------------------------------
rem  Build the project and create the Windows deployment package.
rem  Output: dist\dorm-repair\  and  dist\dorm-repair-windows.zip
rem  Options: -NoMirror  -RunTests  -NoZip   (see build.ps1)
rem  This file is ASCII only on purpose: Chinese text in .bat files
rem  is easily garbled by the console code page. Logic is in build.ps1.
rem ------------------------------------------------------------------
title Dorm Repair System - Build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
echo.
pause
