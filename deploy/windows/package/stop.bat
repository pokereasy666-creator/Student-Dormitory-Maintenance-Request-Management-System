@echo off
rem ------------------------------------------------------------------
rem  Stop the Dorm Repair System. Logic is in scripts\stop.ps1.
rem ------------------------------------------------------------------
title Dorm Repair System - Stop
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop.ps1" %*
echo.
pause
