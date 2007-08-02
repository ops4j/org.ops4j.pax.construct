@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\pax-validate"

set _BATFILE_=%0
set _ID_=
set _URL_=

goto getopts
:shift_2
shift
:shift_1
shift

:getopts
if "%1"=="-i" set _ID_=%2
if "%1"=="-i" goto shift_2
if "%1"=="-u" set _URL_=%2
if "%1"=="-u" goto shift_2
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-add-repository -i repositoryId -u repositoryURL [-- mvnopts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%PAX_CONSTRUCT_OPTIONS% %0 %1 %2 %3 %4 %5 %6 %7 %8 %9

if ""=="%_ID_%" goto request_input
if ""=="%_URL_%" goto request_input
goto skip_input

:request_input
if ""=="%_ID_%" set /p _ID_="repository id (org.ops4j.repository) ? "
if ""=="%_URL_%" set /p _URL_="repository url (http://repository.ops4j.org/maven2) ? "
:skip_input

if ""=="%_ID_%" set _ID_=org.ops4j.repository
if ""=="%_URL_%" set _URL_=http://repository.ops4j.org/maven2

@echo on
mvn pax:add-repository -DrepositoryId=%_ID_% -DrepositoryURL=%_URL_% %_EXTRA_%
:done
