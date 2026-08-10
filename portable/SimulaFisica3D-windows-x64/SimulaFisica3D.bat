@echo off
setlocal enabledelayedexpansion

set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"

rem Prefere um JDK portatil embutido na pasta runtime; senao usa o java do PATH.
if exist "%APP_DIR%runtime\bin\java.exe" (
    set "JAVA_BIN=%APP_DIR%runtime\bin\java.exe"
) else (
    set "JAVA_BIN=java"
)

"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo Java nao foi encontrado neste computador.
    echo.
    echo Opcao 1: instale o Java 21 ^(https://adoptium.net^).
    echo Opcao 2: coloque um JDK 21 portatil de Windows nesta pasta:
    echo   %APP_DIR%runtime
    echo.
    echo Depois execute este arquivo novamente.
    echo.
    pause
    exit /b 1
)

rem O JavaFX (jars com bibliotecas nativas de Windows) entra como modulo nomeado
rem via --module-path. O jar do app (nao-modular) vai apenas no classpath; os
rem jars javafx-* ficam fora do -cp para evitar erros de split-package.
set "APP_CP="
for %%J in ("%APP_DIR%lib\*.jar") do (
    set "JAR_NAME=%%~nxJ"
    if /i not "!JAR_NAME:~0,7!"=="javafx-" (
        if defined APP_CP (set "APP_CP=!APP_CP!;%%J") else (set "APP_CP=%%J")
    )
)

"%JAVA_BIN%" ^
    --module-path "%APP_DIR%lib" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics ^
    -cp "!APP_CP!" ^
    engine.Main %*

if errorlevel 1 (
    echo.
    echo O simulador encerrou com erro.
    pause
)
