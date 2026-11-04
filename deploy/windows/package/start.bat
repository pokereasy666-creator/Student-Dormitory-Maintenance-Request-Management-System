@echo off
rem ------------------------------------------------------------------
rem  Start the Dorm Repair System. Keep this window open while using it;
rem  close the window or press Ctrl+C to stop. Logic is in scripts\start.ps1.
rem  Option: -NoBrowser  (do not open the browser automatically)
rem ------------------------------------------------------------------
title Dorm Repair System
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" %*
echo.
pause
