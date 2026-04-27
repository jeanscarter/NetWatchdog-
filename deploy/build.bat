@echo off
REM ============================================================================
REM  NetWatchdog - Script de Compilación
REM  Compila ambos JARs: servicio (headless) y GUI (configurador)
REM ============================================================================
setlocal

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║         NetWatchdog - Build Script                          ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

set "PROJECT_DIR=%~dp0.."
set "SRC_DIR=%PROJECT_DIR%\src"
set "OUT_DIR=%PROJECT_DIR%\build"
set "DEPLOY_DIR=%PROJECT_DIR%\deploy"

REM ─── Verificar Java ────────────────────────────────────────────────────────
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo  [ERROR] Java no encontrado en PATH. Instale JDK 11+.
        exit /b 1
)

REM ─── Limpiar build anterior ────────────────────────────────────────────────
echo  [1/4] Limpiando directorio de build...
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%\service"
mkdir "%OUT_DIR%\gui"
echo        → Limpio ✓
echo.

REM ─── Compilar Core Service ────────────────────────────────────────────────
echo  [2/4] Compilando Core Service (headless)...
javac -d "%OUT_DIR%\service" ^
      "%SRC_DIR%\com\netwatchdog\service\WatchdogService.java" ^
      "%SRC_DIR%\com\netwatchdog\service\RecoveryEngine.java"
if %errorlevel% neq 0 (
    echo  [ERROR] Fallo en compilación del servicio.
        exit /b 1
)
echo        → Compilado ✓
echo.

REM ─── Crear JAR del Servicio ───────────────────────────────────────────────
echo  [3/4] Empaquetando watchdog-service.jar...
echo Main-Class: com.netwatchdog.service.WatchdogService> "%OUT_DIR%\manifest-svc.txt"
jar cfm "%DEPLOY_DIR%\watchdog-service.jar" ^
    "%OUT_DIR%\manifest-svc.txt" ^
    -C "%OUT_DIR%\service" .
echo        → watchdog-service.jar creado ✓
echo.

REM ─── Compilar y empaquetar GUI ────────────────────────────────────────────
echo  [4/4] Compilando y empaquetando watchdog-gui.jar...

javac -d "%OUT_DIR%\gui" ^
      "%SRC_DIR%\com\netwatchdog\gui\WatchdogGUI.java"
if %errorlevel% neq 0 (
    echo  [ERROR] Fallo en compilación de la GUI.
        exit /b 1
)
echo Main-Class: com.netwatchdog.gui.WatchdogGUI> "%OUT_DIR%\manifest-gui.txt"
jar cfm "%DEPLOY_DIR%\watchdog-gui.jar" ^
    "%OUT_DIR%\manifest-gui.txt" ^
    -C "%OUT_DIR%\gui" .
echo        → watchdog-gui.jar creado ✓
echo.

echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║              BUILD COMPLETADO                               ║
echo  ╠══════════════════════════════════════════════════════════════╣
echo  ║  Artefactos en: deploy\                                     ║
echo  ║    • watchdog-service.jar  (Core Service)                   ║
echo  ║    • watchdog-gui.jar      (Configurador GUI)               ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
