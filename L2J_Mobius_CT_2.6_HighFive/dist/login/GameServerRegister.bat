@echo off
setlocal
pushd "%~dp0"

if not exist "%~dp0GameServerRegister.vbs" (
    echo ERROR: File not found: "%~dp0GameServerRegister.vbs"
    pause
    popd
    exit /b 1
)

cscript.exe //nologo "%~dp0GameServerRegister.vbs"
set "ExitCode=%errorlevel%"

popd

if not "%ExitCode%"=="0" (
    echo.
    echo VBS script finished with error code %ExitCode%.
    pause
)

exit /b %ExitCode%
