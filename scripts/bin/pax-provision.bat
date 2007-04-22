@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _PLATFORM_=





REM while
REM   getopts p:h- SETTING
REM do
REM   case ${SETTING} in
REM     h|\?) echo "pax-provision -p platform [-- mvnopts ...]" ; exit ;;
REM 
REM     p) PLATFORM=${OPTARG} ;;
REM 
REM     -) break ;;
REM   esac
REM done
REM 
REM shift $((${OPTIND}-1))





set _EXTRA_=%*

if ""=="%_PLATFORM_%" set /p _PLATFORM_="OSGi platform (equinox/knopflerfish/felix) ? "
if ""=="%_PLATFORM_%" set _PLATFORM_=equinox

@echo on
echo mvn pax:provision -Dplatform=%_PLATFORM_% %_EXTRA_%
