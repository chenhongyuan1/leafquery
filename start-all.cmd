@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
set "LEAFQUERY_EXIT_CODE=%ERRORLEVEL%"
if "%~1"=="" pause
exit /b %LEAFQUERY_EXIT_CODE%
