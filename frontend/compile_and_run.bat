@echo off
setlocal
cd /d "%~dp0"
echo Compiling SSA Pro Java Swing frontend...
javac SSAApp.java
if errorlevel 1 (
    echo.
    echo Compilation failed. Check the errors above.
    pause
    exit /b 1
)
echo.
echo Starting SSA Pro...
java SSAApp
endlocal
