@echo off
setlocal

set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"

if exist "%APP_DIR%runtime\bin\java.exe" (
    set "JAVA_HOME=%APP_DIR%runtime"
    set "PATH=%APP_DIR%runtime\bin;%PATH%"
) else (
    rem Sem runtime embutido: limpa um JAVA_HOME antigo/incompativel (ex.: JDK 8) para o
    rem lancador interno do Gradle usar o "java" do PATH que validamos abaixo, e nao um
    rem JAVA_HOME desatualizado que causaria UnsupportedClassVersionError.
    set "JAVA_HOME="
)

java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo Java nao foi encontrado neste computador.
    echo.
    echo Para rodar em PC bloqueado, coloque um JDK 21 portatil nesta pasta:
    echo %APP_DIR%runtime
    echo.
    echo Depois execute este arquivo novamente.
    echo.
    pause
    exit /b 1
)

call "%APP_DIR%bin\feira-fisica-computacional-java.bat"
if errorlevel 1 (
    echo.
    echo O simulador encerrou com erro.
    pause
)
