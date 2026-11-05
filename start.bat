@echo off
rem ------------------------------------------------------------------
rem  Dorm Repair System (Python) - start
rem  Start the system. Keep this window open; close it or press Ctrl+C to stop.
rem  Logic is in scripts\windows\start.ps1. This file is ASCII only on
rem  purpose: Chinese text in .bat files is easily garbled by the console.
rem ------------------------------------------------------------------
title Dorm Repair System (Python) - start
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\start.ps1" %*
echo.
pause
