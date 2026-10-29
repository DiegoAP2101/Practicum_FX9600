@echo off
rem ============================================================
rem Compila el proyecto. Requiere JDK (javac en PATH).
rem Si moviste el SDK de Zebra, actualiza SDK_BIN aqui abajo.
rem ============================================================
setlocal
set "SDK_BIN=C:\Program Files\Zebra FXSeries Host Java SDK\RFID\bin"

cd /d "%~dp0"
if not exist out mkdir out

javac -encoding UTF-8 -cp "%SDK_BIN%\Symbol.RFID.API3.jar" -d out src\fx9600demo\*.java
if errorlevel 1 (
    echo.
    echo [ERROR] Fallo la compilacion.
    exit /b 1
)
echo Compilacion OK.
