@echo off
SETLOCAL

:: Configuration des chemins
SET "KEYSTORE_SOURCE=D:\source\ArcheryMonitorKeystore\ArcheryMonitorKeystore.jks"
SET "KEYSTORE_DEST=app\release.jks"
SET "APP_VERSION=v0.1.4-LOCAL"

echo ======================================================
echo    BUILD RELEASE - ARCHERY MONITOR (INTERACTIF)
echo ======================================================
echo.

:: Saisie interactive des secrets (ne s'affichent pas dans le fichier)
set /p SIGNING_KEY_ALIAS="Entrez l'ALIAS de la cle : "
set /p SIGNING_STORE_PASSWORD="Entrez le MOT DE PASSE du Keystore : "
set /p SIGNING_KEY_PASSWORD="Entrez le MOT DE PASSE de la cle (souvent le meme) : "

echo.
echo [1/4] Copie du Keystore...
if exist "%KEYSTORE_SOURCE%" (
    copy /Y "%KEYSTORE_SOURCE%" "%KEYSTORE_DEST%" >nul
) else (
    echo ERREUR : Le fichier Keystore est introuvable au chemin : %KEYSTORE_SOURCE%
    pause
    exit /b 1
)

echo [2/4] Nettoyage du build...
call gradlew.bat clean

echo [3/4] Compilation de la version Release...
:: Lancement de Gradle avec les variables saisies
call gradlew.bat assembleRelease

echo.
echo [4/4] Nettoyage du Keystore temporaire...
if exist "%KEYSTORE_DEST%" (
    del /F /Q "%KEYSTORE_DEST%"
    echo Keystore temporaire supprime.
)

echo.
echo ======================================================
echo Termine ! L'APK se trouve dans :
echo app\build\outputs\apk\release\
echo ======================================================
echo.

ENDLOCAL
pause
