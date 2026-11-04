@echo off
rem [despliegue] Restaura un respaldo en la base de Docker. Uso: restaurar.bat respaldos\nexus-AAAAMMDD-HHMM.sql
rem Reemplaza los datos actuales de nexus por los del respaldo.
setlocal
cd /d "%~dp0"
if "%~1"=="" (echo Uso: restaurar.bat respaldos\archivo.sql & exit /b 1)
if not exist "%~1" (echo No existe %~1 & exit /b 1)
set /p OK=Esto reemplaza los datos de Nexus por los de %~1. Escribe SI para seguir: 
if /i not "%OK%"=="SI" (echo Cancelado. & exit /b 1)
docker compose exec -T mysql sh -c "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\"" < "%~1"
if errorlevel 1 (echo No se pudo restaurar. & exit /b 1)
echo Restaurado: %~1
