@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

if ""=="%PAX_CONSTRUCT_VERSION%" set PAX_CONSTRUCT_VERSION=${project.version}
set PAX_PLUGIN=org.ops4j.pax.construct:maven-pax-plugin:%PAX_CONSTRUCT_VERSION%

set _BATFILE_=%0
set _BUNDLENAME_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-n" set _BUNDLENAME_=%2
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

if ""=="%_BUNDLENAME_%" set /p _BUNDLENAME_="bundleName (myBundle) ? "

if ""=="%_BUNDLENAME_%" set _BUNDLENAME_=myBundle

@echo on
mvn %PAX_PLUGIN%:remove-bundle -DbundleName=%_BUNDLENAME_% %_EXTRA_%
:done
