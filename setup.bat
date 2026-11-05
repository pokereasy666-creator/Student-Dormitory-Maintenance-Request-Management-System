@echo off
rem ------------------------------------------------------------------
rem  Dorm Repair System (Python) - setup
rem  Install Python dependencies into .venv and initialize the database.
rem  Logic is in scripts\windows\setup.ps1. This file is ASCII only on
rem  purpose: Chinese text in .bat files is easily garbled by the console.
rem ------------------------------------------------------------------
title Dorm Repair System (Python) - setup
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\setup.ps1" %*
echo.
pause
