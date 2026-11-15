@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Invoke-CorpusLab.ps1" %*
if "%~1"=="" pause
