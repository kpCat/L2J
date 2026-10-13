@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Runtime.ps1" -Action Stop -RuntimeRoot "%~dp0."
exit /b %errorlevel%
