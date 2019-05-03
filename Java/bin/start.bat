@echo off
setlocal
rem ################################################################
set BIN_DIR=%cd%
set PROJ_DIR=%BIN_DIR%/..
set CLASSES_DIR=%BIN_DIR%/../classes
set JAVA_MAIN_CLASS="com.robi.logger.kslogger.TestB"
rem ################################################################
cd %PROJ_DIR%
rem ################################################################
@echo on
java -cp .;%CLASSES_DIR%; %JAVA_MAIN_CLASS%
@echo off
rem ################################################################
endlocal