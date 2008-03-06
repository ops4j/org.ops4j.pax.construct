@echo off
SETLOCAL
set _SCRIPTDIR_=%~dp0

@REM --------------------------------------------------------------------------
@REM Copyright 2007-2008 Stuart McCulloch.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
@REM implied.
@REM
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM --------------------------------------------------------------------------

if ""=="%PAX_CONSTRUCT_VERSION%" set PAX_CONSTRUCT_VERSION=${project.version}

set _FIND_="find.exe"
if exist "%SystemRoot%\system32\find.exe" set _FIND_="%SystemRoot%\system32\find.exe"
if exist "%SystemRoot%\command\find.exe" set _FIND_="%SystemRoot%\command\find.exe"

call mvn -o -npu -N -f "%_SCRIPTDIR_%\pax-bootstrap-pom.xml" -DPAX_CONSTRUCT_VERSION=%PAX_CONSTRUCT_VERSION% validate | %_FIND_% "ERROR" >NUL
goto answer%ERRORLEVEL%

:answer1
  :: already installed and validated
  goto done

:answer0
  echo BOOTSTRAP PAX-CONSTRUCT PLUGIN
  echo ==============================
  @echo on
  mvn -up -N -f "%_SCRIPTDIR_%\pax-bootstrap-pom.xml" -DPAX_CONSTRUCT_VERSION=%PAX_CONSTRUCT_VERSION% validate

:done
