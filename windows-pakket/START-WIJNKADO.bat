@echo off
setlocal EnableExtensions
title Wijnkado orderuitdraai
cd /d "%~dp0"

set "APP_URL=http://127.0.0.1:8080/"
set "HEALTH_URL=http://127.0.0.1:8080/actuator/health"
set "JAR_FILE=%~dp0WijnkadoAutoparse.jar"
set "JAVA_CMD=%~dp0runtime\bin\java.exe"

cls
echo ============================================================
echo              WIJNKADO ORDERUITDRAAI
echo ============================================================
echo.

if not exist "%JAR_FILE%" goto :missing_jar

if not exist "%JAVA_CMD%" (
    where java.exe >nul 2>&1
    if not errorlevel 1 set "JAVA_CMD=java.exe"
)

if not exist "%JAVA_CMD%" if "%JAVA_CMD%"=="%~dp0runtime\bin\java.exe" (
    echo De portable Java-runtime wordt eenmalig gedownload.
    echo Hiervoor is een internetverbinding nodig. Even geduld...
    echo.
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0installeer-java.ps1"
    if errorlevel 1 goto :missing_java
    set "JAVA_CMD=%~dp0runtime\bin\java.exe"
)

rem Als de applicatie al draait, alleen de browser opnieuw openen.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri '%HEALTH_URL%' -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } } catch {}; exit 1" >nul 2>&1
if not errorlevel 1 (
    echo De Wijnkado-applicatie draait al. De browser wordt geopend.
    start "" "%APP_URL%"
    timeout /t 2 /nobreak >nul
    exit /b 0
)

echo De applicatie wordt gestart. Dit kan de eerste keer even duren.
echo De browser opent automatisch zodra alles klaar is.
echo.
echo LAAT DIT VENSTER OPEN zolang de Wijnkado-pagina wordt gebruikt.
echo Sluit dit venster wanneer je helemaal klaar bent.
echo.

rem Wacht op de healthcheck en open daarna automatisch de browser.
start "" /b powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -Command "$deadline = (Get-Date).AddMinutes(2); do { try { $r = Invoke-WebRequest -UseBasicParsing -Uri '%HEALTH_URL%' -TimeoutSec 2; if ($r.StatusCode -eq 200) { Start-Process '%APP_URL%'; exit 0 } } catch {}; Start-Sleep -Seconds 1 } while ((Get-Date) -lt $deadline)" >nul 2>&1

"%JAVA_CMD%" -jar "%JAR_FILE%" --server.address=127.0.0.1
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
    echo De Wijnkado-applicatie is gestopt.
) else (
    echo De applicatie kon niet worden gestart of is onverwacht gestopt.
    echo Maak een foto van de foutmelding hierboven en stuur die door.
)
echo.
pause
exit /b %EXIT_CODE%

:missing_jar
echo FOUT: WijnkadoAutoparse.jar staat niet in deze map.
echo Kopieer of pak het volledige Windows-pakket opnieuw uit.
echo.
pause
exit /b 1

:missing_java
echo FOUT: De meegeleverde Java-runtime ontbreekt en Java is niet geinstalleerd.
echo Kopieer of pak het volledige Windows-pakket opnieuw uit.
echo.
echo De Java-downloadpagina wordt voor de zekerheid geopend.
start "" "https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64&package=jre"
pause
exit /b 1
