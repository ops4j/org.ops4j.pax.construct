@echo off
SETLOCAL
set _SCRIPTS_=%~dp0

if ""=="%PAX_CONSTRUCT_VERSION%" set PAX_CONSTRUCT_VERSION=${project.version}

call mvn -q -o -npu -N -f "%_SCRIPTS_%\pax-bootstrap-pom.xml" -DPAX_CONSTRUCT_VERSION=%PAX_CONSTRUCT_VERSION% validate
goto answer%ERRORLEVEL%

:answer0
  :: already installed and validated
  goto done

:answer1
  echo BOOTSTRAP PAX-CONSTRUCT PLUGIN
  echo ==============================
  @echo on
  mvn -up -N -f "%_SCRIPTS_%\pax-bootstrap-pom.xml" -DPAX_CONSTRUCT_VERSION=%PAX_CONSTRUCT_VERSION% validate

:done
