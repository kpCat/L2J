@echo off
setlocal
pushd "%~dp0"

if not exist "%~dp0GameServer.vbs" (
    echo ERROR: File not found: "%~dp0GameServer.vbs"
    pause
    popd
    exit /b 1
)

cscript.exe //nologo "%~dp0GameServer.vbs"
set "ExitCode=%errorlevel%"

popd

if not "%ExitCode%"=="0" (
    echo.
    echo VBS script finished with error code %ExitCode%.
    pause
)

exit /b %ExitCode%
