@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _PACKAGE_=
set _NAME_=
set _VERSION_=





REM NUMOPTS=$#
REM while
REM   getopts p:n:v:h- SETTING
REM do
REM   case ${SETTING} in
REM     h|\?) echo "pax-create-bundle -p javaPackage -n bundleName [-v version ] [-- mvnOpts ...]" ; exit ;;
REM 
REM     p) PACKAGE=${OPTARG} ;;
REM     n) NAME=${OPTARG} ;;
REM     v) VERSION=${OPTARG} ;;
REM 
REM     -) break ;;
REM   esac
REM done
REM 
REM shift $((${OPTIND}-1))





set _EXTRA_=%*

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
echo mvn pax:create-bundle -Dpackage=%_PACKAGE_% -Dname=%_NAME_% -Dversion=%_VERSION_% %_EXTRA_%
