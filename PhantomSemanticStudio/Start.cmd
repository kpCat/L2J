@echo off
chcp 65001 >nul
cd /d "%~dp0"
where dotnet >nul 2>nul
if errorlevel 1 goto no_sdk
dotnet run --project "src\PhantomSemanticStudio.WinForms\PhantomSemanticStudio.WinForms.csproj" -c Release
if errorlevel 1 goto failed
exit /b 0
:no_sdk
echo Нужен .NET 10 SDK или Visual Studio 2026 с компонентом разработки .NET Desktop.
pause
exit /b 1
:failed
echo Сборка или запуск не удались. Передайте вывод Codex; не заменяйте файлы L2J.
pause
exit /b 1
