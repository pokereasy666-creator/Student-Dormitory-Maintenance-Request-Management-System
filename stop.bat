@echo off
rem ------------------------------------------------------------------
rem  Dorm Repair System (Python) - stop
rem  Stop the system.
rem  Logic is in scripts\windows\stop.ps1. This file is ASCII only on
rem  purpose: Chinese text in .bat files is easily garbled by the console.
rem ------------------------------------------------------------------
title Dorm Repair System (Python) - stop
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\stop.ps1" %*
echo.
pause
