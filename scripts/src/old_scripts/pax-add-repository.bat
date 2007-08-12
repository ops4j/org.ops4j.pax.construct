@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

if ""=="%PAX_CONSTRUCT_VERSION%" set PAX_CONSTRUCT_VERSION=${project.version}
set PAX_PLUGIN=org.ops4j.pax.construct:maven-pax-plugin:%PAX_CONSTRUCT_VERSION%

set _BATFILE_=%0
set _REPOSITORYID_=
set _REPOSITORYURL_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-i" set _REPOSITORYID_=%2
if "%1"=="-i" goto shift_2
if "%1"=="-u" set _REPOSITORYURL_=%2
if "%1"=="-u" goto shift_2
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-add-repository -i repositoryId -u repositoryURL [-- mvnOpts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%PAX_CONSTRUCT_OPTIONS% %0 %1 %2 %3 %4 %5 %6 %7 %8 %9

if ""=="%_REPOSITORYID_%" set /p _REPOSITORYID_="repositoryId (org.ops4j.repository) ? "
if ""=="%_REPOSITORYURL_%" set /p _REPOSITORYURL_="repositoryURL (http://repository.ops4j.org/maven2) ? "

if ""=="%_REPOSITORYID_%" set _REPOSITORYID_=org.ops4j.repository
if ""=="%_REPOSITORYURL_%" set _REPOSITORYURL_=http://repository.ops4j.org/maven2

@echo on
mvn %PAX_PLUGIN%:add-repository -DrepositoryId=%_REPOSITORYID_% -DrepositoryURL=%_REPOSITORYURL_% %_EXTRA_%
:done
