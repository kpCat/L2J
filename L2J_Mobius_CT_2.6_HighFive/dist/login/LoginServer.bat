@echo off
setlocal
pushd "%~dp0"

if not exist "%~dp0LoginServer.vbs" (
    echo ERROR: File not found: "%~dp0LoginServer.vbs"
    pause
    popd
    exit /b 1
)

cscript.exe //nologo "%~dp0LoginServer.vbs"
set "ExitCode=%errorlevel%"

popd

if not "%ExitCode%"=="0" (
    echo.
    echo VBS script finished with error code %ExitCode%.
    pause
)

exit /b %ExitCode%
