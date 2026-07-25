@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Place this file in the dist folder and run it once.
rem It creates one BAT wrapper next to every VBS file in game and login.

call :CreateForFolder "%~dp0game"
call :CreateForFolder "%~dp0login"

echo.
echo BAT wrappers have been generated.
pause
exit /b 0

:CreateForFolder
set "TargetFolder=%~1"
if not exist "%TargetFolder%\" (
    echo Folder not found: "%TargetFolder%"
    exit /b 0
)

for %%F in ("%TargetFolder%\*.vbs") do (
    if exist "%%~fF" call :CreateWrapper "%%~fF"
)
exit /b 0

:CreateWrapper
set "VbsFile=%~1"
set "BatFile=%~dpn1.bat"
set "VbsName=%~nx1"

> "%BatFile%" echo @echo off
>>"%BatFile%" echo setlocal
>>"%BatFile%" echo pushd "%%~dp0"
>>"%BatFile%" echo.
>>"%BatFile%" echo if not exist "%%~dp0%VbsName%" ^(
>>"%BatFile%" echo     echo ERROR: File not found: "%%~dp0%VbsName%"
>>"%BatFile%" echo     pause
>>"%BatFile%" echo     popd
>>"%BatFile%" echo     exit /b 1
>>"%BatFile%" echo ^)
>>"%BatFile%" echo.
>>"%BatFile%" echo cscript.exe //nologo "%%~dp0%VbsName%"
>>"%BatFile%" echo set "ExitCode=%%errorlevel%%"
>>"%BatFile%" echo.
>>"%BatFile%" echo popd
>>"%BatFile%" echo.
>>"%BatFile%" echo if not "%%ExitCode%%"=="0" ^(
>>"%BatFile%" echo     echo.
>>"%BatFile%" echo     echo VBS script finished with error code %%ExitCode%%.
>>"%BatFile%" echo     pause
>>"%BatFile%" echo ^)
>>"%BatFile%" echo.
>>"%BatFile%" echo exit /b %%ExitCode%%

echo Created: "%BatFile%"
exit /b 0
