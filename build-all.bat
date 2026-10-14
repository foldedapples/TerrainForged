@echo off
REM Builds and tests everything: engine tests, Fabric jar, Forge jar.  Made by Freak4U.
REM Keeps going if one step fails and shows a summary, and waits for a key so the window stays open.
setlocal
cd /d "%~dp0"
set RESULT_TESTS=FAILED
set RESULT_FABRIC=FAILED
set RESULT_FORGE=FAILED

echo === [1/3] Engine tests ===
call gradlew.bat engineTests && set RESULT_TESTS=OK
echo.
echo === [2/3] Fabric jar ===
call gradlew.bat -p fabric build && set RESULT_FABRIC=OK
echo.
echo === [3/3] Forge jar ===
call gradlew.bat -p forge build && set RESULT_FORGE=OK

echo.
echo ================= SUMMARY =================
echo  Engine tests : %RESULT_TESTS%
echo  Fabric jar   : %RESULT_FABRIC%   (fabric\build\libs\)
echo  Forge jar    : %RESULT_FORGE%   (forge\build\libs\)
echo ===========================================
echo If something FAILED, scroll up to the first red "FAILURE" / "What went wrong" block.
pause
