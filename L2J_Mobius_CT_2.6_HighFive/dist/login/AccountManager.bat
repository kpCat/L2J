@echo off
setlocal
pushd "%~dp0"

if not exist "%~dp0AccountManager.vbs" (
    echo ERROR: File not found: "%~dp0AccountManager.vbs"
    pause
    popd
    exit /b 1
)

cscript.exe //nologo "%~dp0AccountManager.vbs"
set "ExitCode=%errorlevel%"

popd

if not "%ExitCode%"=="0" (
    echo.
    echo VBS script finished with error code %ExitCode%.
    pause
)

exit /b %ExitCode%
