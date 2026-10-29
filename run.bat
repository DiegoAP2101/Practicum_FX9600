@echo off
rem ============================================================
rem Ejecuta la demo conectando con la lectora FX9600.
rem La IP y puertos se configuran en config.properties.
rem Si moviste el SDK de Zebra, actualiza SDK_BIN aqui abajo.
rem ============================================================
setlocal
set "SDK_BIN=C:\Program Files\Zebra FXSeries Host Java SDK\RFID\bin"

cd /d "%~dp0"
if not exist out\fx9600demo\Main.class (
    echo Primera vez: compilando...
    call build.bat
    if errorlevel 1 exit /b 1
)

rem Las DLL nativas del SDK deben estar visibles para la JVM:
rem   RFIDAPI3_JNI_HOST.dll queda en C:\Windows\System32 (la instala el MSI)
rem   RFIDAPI32PC.dll queda en la carpeta bin del SDK
set "PATH=%SDK_BIN%;%PATH%"
java -cp "out;%SDK_BIN%\Symbol.RFID.API3.jar" -Djava.library.path="%SDK_BIN%;C:\Windows\System32" fx9600demo.Main %*
