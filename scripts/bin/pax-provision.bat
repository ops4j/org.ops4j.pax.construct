@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _BATFILE_=%0
set _PLATFORM_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-p" set _PLATFORM_=%2
if "%1"=="-p" goto shift_2
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-provision -p platform [-- mvnopts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%PAX_CONSTRUCT_OPTIONS% %0 %1 %2 %3 %4 %5 %6 %7 %8 %9

if ""=="%_PLATFORM_%" set /p _PLATFORM_="OSGi platform (equinox/knopflerfish/felix) ? "
if ""=="%_PLATFORM_%" set _PLATFORM_=equinox

@echo on
mvn pax:provision -Dplatform=%_PLATFORM_% %_EXTRA_%
:done
