@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

set "PROJECT_DIR=%~dp0backend"
set "PID_FILE=%~dp0.sentiment.pid"
set "LOG_FILE=%~dp0logs\sentiment.log"

if not exist "%~dp0logs" mkdir "%~dp0logs"

if "%1"=="" goto :help
if "%1"=="start" goto :start
if "%1"=="stop" goto :stop
if "%1"=="restart" goto :restart
if "%1"=="status" goto :status
if "%1"=="log" goto :log
if "%1"=="start-collector" goto :start_collector
if "%1"=="stop-collector" goto :stop_collector
if "%1"=="start-analyzer" goto :start_analyzer
if "%1"=="stop-analyzer" goto :stop_analyzer
goto :help

:start
call :check_running
if !running!==1 (
    echo [WARN] 后端已在运行中 (PID: !pid!)
    echo        使用 "ctl.bat restart" 重启
    goto :eof
)
echo [INFO] 正在启动舆情分析平台...
cd /d "%PROJECT_DIR%"
start /b cmd /c "mvn spring-boot:run > "%LOG_FILE%" 2>&1"
for /f "tokens=2" %%a in ('tasklist /fi "windowtitle eq spring-boot*" /fo list ^| findstr PID') do set "NEW_PID=%%a"

:: Wait for startup
echo [INFO] 等待服务启动...
set /a count=0
:wait_loop
timeout /t 2 /nobreak >nul
set /a count+=2
curl -s -o nul -w "%%{http_code}" http://localhost:9090/api/dashboard/stats >nul 2>&1
if !errorlevel!==0 (
    echo [OK] 服务启动成功!
    echo      后端: http://localhost:9090
    echo      日志: %LOG_FILE%
    echo      控制: ctl.bat status / stop / log
    goto :eof
)
if !count! geq 60 (
    echo [ERROR] 启动超时，请检查日志: %LOG_FILE%
    goto :eof
)
goto :wait_loop

:stop
call :check_running
if !running!==0 (
    echo [INFO] 后端未在运行
    goto :eof
)
echo [INFO] 正在停止后端 (PID: !pid!)...
taskkill /f /pid !pid! >nul 2>&1
:: Also kill child java processes
for /f "tokens=2" %%a in ('wmic process where "parentprocessid=!pid!" get processid /value 2^>nul ^| findstr ProcessId') do (
    taskkill /f /pid %%a >nul 2>&1
)
echo [OK] 后端已停止
goto :eof

:restart
echo [INFO] 重启中...
call :stop
timeout /t 3 /nobreak >nul
call :start
goto :eof

:status
echo ============================================
echo        舆情分析平台 - 系统状态
echo ============================================

:: Backend status
call :check_running
if !running!==1 (
    echo  后端服务:  [运行中] PID: !pid!
    :: Try to get API stats
    for /f "delims=" %%a in ('curl -s http://localhost:9090/api/dashboard/stats 2^>nul') do set "stats=%%a"
    if defined stats (
        echo  API状态:   [正常]
    ) else (
        echo  API状态:   [启动中]
    )
) else (
    echo  后端服务:  [未运行]
)

:: Frontend status
netstat -ano | findstr ":5190 :5191 :5192" | findstr "LISTENING" >nul 2>&1
if !errorlevel!==0 (
    echo  前端服务:  [运行中]
) else (
    echo  前端服务:  [未运行]
)

:: MySQL status
netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if !errorlevel!==0 (
    echo  MySQL:     [运行中]
) else (
    echo  MySQL:     [未运行]
)

:: Redis status
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if !errorlevel!==0 (
    echo  Redis:     [运行中]
) else (
    echo  Redis:     [未运行]
)

echo ============================================

:: Collector control API (if backend is running)
if !running!==1 (
    echo.
    echo  模块心跳:
    curl -s http://localhost:9090/api/settings/health 2>nul
    echo.
)
goto :eof

:log
if exist "%LOG_FILE%" (
    echo [INFO] 最近日志 (最后50行):
    echo --------------------------------------------
    powershell -command "Get-Content '%LOG_FILE%' -Tail 50"
) else (
    echo [WARN] 日志文件不存在: %LOG_FILE%
)
goto :eof

:start_collector
echo [INFO] 启用采集模块...
curl -s -X PUT "http://localhost:9090/api/settings/collector/enable" -H "Content-Type: application/json" -d "{\"enabled\":true}" 2>nul
echo [OK] 采集模块已启用
goto :eof

:stop_collector
echo [INFO] 禁用采集模块...
curl -s -X PUT "http://localhost:9090/api/settings/collector/disable" -H "Content-Type: application/json" -d "{\"enabled\":false}" 2>nul
echo [OK] 采集模块已禁用
goto :eof

:start_analyzer
echo [INFO] 启用分析模块...
curl -s -X PUT "http://localhost:9090/api/settings/analyzer/enable" -H "Content-Type: application/json" -d "{\"enabled\":true}" 2>nul
echo [OK] 分析模块已启用
goto :eof

:stop_analyzer
echo [INFO] 禁用分析模块...
curl -s -X PUT "http://localhost:9090/api/settings/analyzer/disable" -H "Content-Type: application/json" -d "{\"enabled\":false}" 2>nul
echo [OK] 分析模块已禁用
goto :eof

:check_running
set "running=0"
set "pid="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":9090" ^| findstr "LISTENING"') do set "pid=%%a"
if defined pid set "running=1"
goto :eof

:help
echo ============================================
echo        舆情分析平台 - 控制脚本
echo ============================================
echo.
echo  用法: ctl.bat [命令]
echo.
echo  命令:
echo    start          启动后端服务
echo    stop           停止后端服务
echo    restart        重启后端服务
echo    status         查看系统状态
echo    log            查看最近日志
echo.
echo    start-collector  启用采集模块
echo    stop-collector   禁用采集模块
echo    start-analyzer   启用分析模块
echo    stop-analyzer    禁用分析模块
echo.
echo  示例:
echo    ctl.bat start     启动服务
echo    ctl.bat status    查看状态
echo    ctl.bat stop      停止服务
echo.
echo ============================================
goto :eof
