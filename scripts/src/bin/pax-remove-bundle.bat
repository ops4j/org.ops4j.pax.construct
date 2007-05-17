@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _BATFILE_=%0
set _NAME_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-n" set _NAME_=%2
if "%1"=="-n" goto shift_2
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-remove-bundle -n bundleName [-- mvnOpts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%PAX_CONSTRUCT_OPTIONS% %0 %1 %2 %3 %4 %5 %6 %7 %8 %9

if ""=="%_NAME_%" set /p _NAME_="bundle name (myBundle) ? "
if ""=="%_NAME_%" set _NAME_=myBundle

@echo on
mvn pax:remove-bundle -Dname=%_NAME_% %_EXTRA_%
:done
