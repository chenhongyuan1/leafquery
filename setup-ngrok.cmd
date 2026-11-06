@echo off
setlocal
call "%~dp0ngrok-weather.cmd" setup
set "LEAFQUERY_NGROK_SETUP_EXIT_CODE=%ERRORLEVEL%"
pause
exit /b %LEAFQUERY_NGROK_SETUP_EXIT_CODE%
