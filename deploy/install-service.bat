@echo off
REM ============================================================================
REM  NetWatchdog - Script de Instalación del Servicio Windows
REM  EJECUTAR COMO ADMINISTRADOR
REM ============================================================================
setlocal

REM ─── Modo silencioso (invocado desde la GUI) ────────────────────────────
set "SILENT=0"
if /i "%~1"=="/silent" set "SILENT=1"

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║       NetWatchdog - Instalador del Servicio Windows         ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

REM ─── Verificar privilegios de administrador ────────────────────────────────
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo  [ERROR] Este script requiere permisos de Administrador.
    echo  Haga clic derecho en el archivo y seleccione "Ejecutar como administrador".
    echo.
    if "%SILENT%"=="0" pause
    exit /b 1
)

REM ─── Variables ─────────────────────────────────────────────────────────────
set "INSTALL_DIR=C:\ProgramData\NetWatchdog"
set "SCRIPT_DIR=%~dp0"

echo  [1/5] Creando directorio de instalación...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%INSTALL_DIR%\winsw-logs" mkdir "%INSTALL_DIR%\winsw-logs"
echo        → %INSTALL_DIR% ✓
echo.

echo  [2/5] Verificando archivos requeridos...
if not exist "%SCRIPT_DIR%watchdog-service.exe" (
    echo  [ERROR] No se encontró watchdog-service.exe
    echo  Descargue WinSW desde: https://github.com/winsw/winsw/releases
    echo  Renombre el .exe a: watchdog-service.exe
    if "%SILENT%"=="0" pause
    exit /b 1
)
if not exist "%SCRIPT_DIR%watchdog-service.jar" (
    echo  [ERROR] No se encontró watchdog-service.jar
    echo  Compile el servicio primero con build-service.bat
    if "%SILENT%"=="0" pause
    exit /b 1
)
if not exist "%SCRIPT_DIR%watchdog-service.xml" (
    echo  [ERROR] No se encontró watchdog-service.xml
    if "%SILENT%"=="0" pause
    exit /b 1
)
echo        → Todos los archivos encontrados ✓
echo.

echo  [3/5] Copiando archivos al directorio de instalación...
copy /Y "%SCRIPT_DIR%watchdog-service.exe" "%INSTALL_DIR%\" >nul
copy /Y "%SCRIPT_DIR%watchdog-service.jar" "%INSTALL_DIR%\" >nul
copy /Y "%SCRIPT_DIR%watchdog-service.xml" "%INSTALL_DIR%\" >nul
echo        → Archivos copiados ✓
echo.

echo  [4/5] Instalando servicio Windows...
pushd "%INSTALL_DIR%"
watchdog-service.exe install
if %errorlevel% neq 0 (
    echo  [WARN] El servicio puede ya estar instalado. Intentando reinstalar...
    watchdog-service.exe uninstall
    timeout /t 2 /nobreak >nul
    watchdog-service.exe install
)
popd
echo        → Servicio instalado ✓
echo.

echo  [5/5] Iniciando servicio...
pushd "%INSTALL_DIR%"
watchdog-service.exe start
popd
echo        → Servicio iniciado ✓
echo.

echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║                 INSTALACIÓN COMPLETADA                      ║
echo  ╠══════════════════════════════════════════════════════════════╣
echo  ║  Servicio: NetWatchdog - Auto Recovery Agent                ║
echo  ║  Estado  : Ejecutándose bajo NT AUTHORITY\SYSTEM            ║
echo  ║  Config  : %INSTALL_DIR%\config.properties      ║
echo  ║  Log     : %INSTALL_DIR%\watchdog.log           ║
echo  ║                                                             ║
echo  ║  Para configurar, ejecute: watchdog-gui.jar                 ║
echo  ║  Para administrar: services.msc                             ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
if "%SILENT%"=="0" pause
