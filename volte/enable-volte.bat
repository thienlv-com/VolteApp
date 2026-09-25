@echo off
rem Bat lai VoLTE cho 2 SIM sau moi lan khoi dong lai dien thoai (can cam cap + USB debugging)
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" wait-for-device
"%ADB%" push "%~dp0volte.dex" /data/local/tmp/volte.dex >nul
rem Lay subId hien tai cua cac SIM dang hoat dong roi ghi de carrier config
for /f "tokens=*" %%s in ('"%ADB%" shell "dumpsys isub | grep -oE 'sSlotIndexToSubId\[[0-9]\]: subIds=[0-9]+=\[[0-9]+\]' | grep -oE '\[[0-9]+\]$' | tr -d '[]' | tr '\n' ' '"') do set SUBS=%%s
if "%SUBS%"=="" set SUBS=1 2
echo SubIds: %SUBS%
"%ADB%" shell "CLASSPATH=/data/local/tmp/volte.dex app_process / VolteEnabler %SUBS%"
"%ADB%" shell "cmd phone ims enable -s 0; cmd phone ims enable -s 1"
echo Xong. Doi 10-20 giay de IMS dang ky.
pause
