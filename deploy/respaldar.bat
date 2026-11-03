@echo off
rem [despliegue] Respaldo de la base de Nexus que corre en Docker. Uso: respaldar.bat
rem Deja deploy\respaldos\nexus-AAAAMMDD-HHMM.sql (fuera de git).
setlocal
cd /d "%~dp0"
if not exist respaldos mkdir respaldos
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmm"') do set FECHA=%%i
docker compose exec -T mysql sh -c "mysqldump -uroot -p\"$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF --databases nexus" > "respaldos\nexus-%FECHA%.sql"
if errorlevel 1 (echo No se pudo respaldar. & exit /b 1)
echo Respaldo listo: deploy\respaldos\nexus-%FECHA%.sql
