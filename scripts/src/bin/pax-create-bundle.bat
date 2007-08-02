@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

if ""=="%PAX_CONSTRUCT_VERSION%" set PAX_CONSTRUCT_VERSION=${project.version}
set PAX_PLUGIN=org.ops4j.pax.construct:maven-pax-plugin:%PAX_CONSTRUCT_VERSION%

set _BATFILE_=%0
set _PACKAGE_=
set _NAME_=
set _VERSION_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-p" set _PACKAGE_=%2
if "%1"=="-p" goto shift_2
if "%1"=="-n" set _NAME_=%2
if "%1"=="-n" goto shift_2
if "%1"=="-v" set _VERSION_=%2
if "%1"=="-v" goto shift_2
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-create-bundle -p javaPackage -n bundleName [-v version ] [-- mvnOpts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%PAX_CONSTRUCT_OPTIONS% %0 %1 %2 %3 %4 %5 %6 %7 %8 %9

if ""=="%_PACKAGE_%" goto request_input
if ""=="%_NAME_%" goto request_input
goto skip_input

:request_input
if ""=="%_PACKAGE_%" set /p _PACKAGE_="java package (org.ops4j.example) ? "
if ""=="%_NAME_%" set /p _NAME_="bundle name (myBundle) ? "
if ""=="%_VERSION_%" set /p _VERSION_="bundle version (0.1.0-SNAPSHOT) ? "
:skip_input

if ""=="%_PACKAGE_%" set _PACKAGE_=org.ops4j.example
if ""=="%_NAME_%" set _NAME_=myBundle
if ""=="%_VERSION_%" set _VERSION_=0.1.0-SNAPSHOT

@echo on
mvn %PAX_PLUGIN%:create-bundle -Dpackage=%_PACKAGE_% -Dname=%_NAME_% -Dversion=%_VERSION_% %_EXTRA_%
:done
