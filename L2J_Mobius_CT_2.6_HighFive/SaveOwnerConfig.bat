@echo off
setlocal
chcp 65001 >nul
echo Сохранение публичных настроек владельца...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OwnerConfig.ps1" -Action Save
if not "%errorlevel%"=="0" goto save_error
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OwnerConfig.ps1" -Action Verify
if not "%errorlevel%"=="0" goto verify_error
echo.
echo Настройки успешно сохранены и проверены. Изменения можно коммитить и отправлять через GitKraken
pause
exit /b 0

:save_error
echo.
echo Ошибка сохранения настроек. Успех не подтверждён. Прочитайте описание конфликта выше.
pause
exit /b 1

:verify_error
echo.
echo Ошибка проверки настроек. Успех не подтверждён. Прочитайте описание конфликта выше.
pause
exit /b 1
