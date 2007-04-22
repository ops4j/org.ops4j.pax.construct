@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _BATFILE_=%0
set _GROUPID_=
set _ARTIFACTID_=
set _VERSION_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-g" set _GROUPID_=%2
if "%1"=="-g" goto shift_2
if "%1"=="-a" set _ARTIFACTID_=%2
if "%1"=="-a" goto shift_2
if "%1"=="-v" set _VERSION_=%2
if "%1"=="-v" goto shift_2
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-import-bundle -g groupId -a artifactId -v version [-- mvnopts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%0 %1 %2 %3 %4 %5 %6 %7 %8 %9

if ""=="%_GROUPID_%" set /p _GROUPID_="bundle groupId (org.ops4j.example) ? "
if ""=="%_ARTIFACTID_%" set /p _ARTIFACTID_="bundle artifactId (myBundle) ? "
if ""=="%_VERSION_%" set /p _VERSION_="bundle version (0.1.0-SNAPSHOT) ? "

if ""=="%_GROUPID_%" set _GROUPID_=org.ops4j.example
if ""=="%_ARTIFACTID_%" set _ARTIFACTID_=myBundle
if ""=="%_VERSION_%" set _VERSION_=0.1.0-SNAPSHOT

@echo on
mvn pax:import-bundle -DgroupId=%_GROUPID_% -DartifactId=%_ARTIFACTID_% -Dversion=%_VERSION_% %_EXTRA_%
:done
