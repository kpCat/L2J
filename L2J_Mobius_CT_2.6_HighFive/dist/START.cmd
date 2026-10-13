@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Runtime.ps1" -Action Start -RuntimeRoot "%~dp0."
exit /b %errorlevel%
