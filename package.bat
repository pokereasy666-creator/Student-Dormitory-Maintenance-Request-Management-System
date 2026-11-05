@echo off
rem ------------------------------------------------------------------
rem  Dorm Repair System (Python) - package
rem  Create dist\dorm-repair-py-windows.zip for deploying to another computer.
rem  Logic is in scripts\windows\package.ps1. This file is ASCII only on
rem  purpose: Chinese text in .bat files is easily garbled by the console.
rem ------------------------------------------------------------------
title Dorm Repair System (Python) - package
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\package.ps1" %*
echo.
pause
