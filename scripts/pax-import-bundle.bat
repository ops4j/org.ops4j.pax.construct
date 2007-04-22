@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _GROUPID_=
set _ARTIFACTID_=
set _VERSION_=





REM while
REM   getopts g:a:v:h- SETTING
REM do
REM   case ${SETTING} in
REM     h|\?) echo "pax-import-bundle -g groupId -a artifactId -v version [-- mvnopts ...]" ; exit ;;
REM 
REM     g) GROUPID=${OPTARG} ;;
REM     a) ARTIFACTID=${OPTARG} ;;
REM     v) VERSION=${OPTARG} ;;
REM 
REM     -) break ;;
REM   esac
REM done
REM 
REM shift $((${OPTIND}-1))





set _EXTRA_=%*

if ""=="%_GROUPID_%" set /p _GROUPID_="bundle groupId (org.ops4j.example) ? "
if ""=="%_ARTIFACTID_%" set /p _ARTIFACTID_="bundle artifactId (myBundle) ? "
if ""=="%_VERSION_%" set /p _VERSION_="bundle version (0.1.0-SNAPSHOT) ? "

if ""=="%_GROUPID_%" set _GROUPID_=org.ops4j.example
if ""=="%_ARTIFACTID_%" set _ARTIFACTID_=myBundle
if ""=="%_VERSION_%" set _VERSION_=0.1.0-SNAPSHOT

@echo on
echo mvn pax:import-bundle -DgroupId=%_GROUPID_% -DartifactId=%_ARTIFACTID_% -Dversion=%_VERSION_% %_EXTRA_%
