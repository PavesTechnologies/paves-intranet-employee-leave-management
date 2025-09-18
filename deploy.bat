 @echo off
 setlocal enabledelayedexpansion

 REM ================================
 REM CONFIGURATION
 REM ================================
 set VM_USER=pavesadmin
 set VM_PASS=paves@2025
 set VM_HOST=192.168.2.37
 set VM_TARGET_DIR=/home/pavesadmin/leavemanagement-app
 set JAR_PATH=target\employee-leave-management-0.0.1-SNAPSHOT.jar

 REM Paths to PuTTY tools
 set PSCP_PATH=pscp.exe
 set PLINK_PATH=plink.exe

 REM ================================
 REM CHECK VERBOSE MODE
 REM ================================
 set VERBOSE=false
 if /I "%~1"=="verbose" set VERBOSE=true

 if "%VERBOSE%"=="true" (
     echo [INFO] Running in VERBOSE mode — all logs will be shown.
 ) else (
     echo [INFO] Running in NORMAL mode — only key messages will be shown.
 )

 echo ==========================================================
 echo [INFO] Starting build and deployment process
 echo ==========================================================

 REM Step 1: Build the project
 echo [INFO] Building the project...
 if "%VERBOSE%"=="true" (
     call mvnw clean install -DskipTests
 ) else (
     call mvnw clean install -DskipTests >nul 2>&1
 )
 if %errorlevel% neq 0 (
     echo [ERROR] Maven build failed! Exiting...
     exit /b 1
 )

 REM Step 2: Upload JAR to VM
 echo [INFO] Uploading JAR to VM...
 if "%VERBOSE%"=="true" (
     %PSCP_PATH% -pw %VM_PASS% "%JAR_PATH%" %VM_USER%@%VM_HOST%:%VM_TARGET_DIR%
 ) else (
     %PSCP_PATH% -q -pw %VM_PASS% "%JAR_PATH%" %VM_USER%@%VM_HOST%:%VM_TARGET_DIR%
 )
 if %errorlevel% neq 0 (
     echo [ERROR] Failed to upload JAR! Exiting...
     exit /b 1
 )

 REM Step 3: Restart service via SSH
 echo [INFO] Restarting leavemanagement.service on VM...
 if "%VERBOSE%"=="true" (
     %PLINK_PATH% -pw %VM_PASS% %VM_USER%@%VM_HOST% "echo %VM_PASS% | sudo -S systemctl restart leavemanagement.service"
 ) else (
     %PLINK_PATH% -pw %VM_PASS% %VM_USER%@%VM_HOST% "echo %VM_PASS% | sudo -S systemctl restart leavemanagement.service" >nul 2>&1
 )
 if %errorlevel% neq 0 (
     echo [ERROR] Failed to restart service! Exiting...
     exit /b 1
 )

 REM Step 4: Show service logs (last 6 lines in normal mode)
 echo [INFO] Fetching service logs...
 if "%VERBOSE%"=="true" (
     %PLINK_PATH% -pw %VM_PASS% %VM_USER%@%VM_HOST% "journalctl -u leavemanagement.service -n 20 --no-pager"
 ) else (
     %PLINK_PATH% -pw %VM_PASS% %VM_USER%@%VM_HOST% "journalctl -u leavemanagement.service -n 6 --no-pager"
 )

 REM Step 5: Done
 echo ==========================================================
 echo [SUCCESS] Deployment complete!
 echo ==========================================================

 endlocal
 pause
