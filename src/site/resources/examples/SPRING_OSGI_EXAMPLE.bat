
REM --------------------------------
REM  let's start with a new project
REM --------------------------------

call pax-create-project -g examples -a spring

cd spring

REM ---------------------------------------------------------------------
REM  basic Spring repositories, so we can import existing Spring bundles
REM ---------------------------------------------------------------------

call pax-add-repository -i spring-milestones -u http://s3.amazonaws.com/maven.springframework.org/milestone
call pax-add-repository -i spring-snapshots -u http://static.springframework.org/maven2-snapshots -- -Dsnapshots "-Dreleases=false"

REM ---------------------------------------------------------------------------
REM  importTransitive also imports any bundles spring-osgi-extender depends on
REM    (use widenScope to also consider non-provided dependencies)
REM ---------------------------------------------------------------------------

call pax-import-bundle -g org.springframework.osgi -a spring-osgi-extender -v 1.0-rc1-SNAPSHOT -- -DwidenScope -DimportTransitive

REM -----------------------------------
REM  basic SLF4J implementation bundle
REM -----------------------------------

call pax-import-bundle -g org.slf4j -a slf4j-simple -v 1.4.3

REM -----------------------------------------------------------------------------------
REM  put wrapped jars in a subdirectory and add the wrapped version to each entry name
REM -----------------------------------------------------------------------------------

set OLD_CONSTRUCT_OPTIONS=%PAX_CONSTRUCT_OPTIONS%
set PAX_CONSTRUCT_OPTIONS=-DaddVersion -DtargetDirectory=wrappers %OLD_CONSTRUCT_OPTIONS%

call pax-wrap-jar -a backport-util-concurrent -v 3.0 -- "-DimportPackage=sun.misc;resolution:=optional,*"

call pax-wrap-jar -a aopalliance -v 1.0
call pax-wrap-jar -a asm -v 2.2.3
call pax-wrap-jar -a commons-collections -v 3.2
call pax-wrap-jar -g javax.servlet -a jsp-api -v 2.0
call pax-wrap-jar -g javax.servlet -a servlet-api -v 2.5
call pax-wrap-jar -a junit -v 3.8.2

REM ---------------------------------
REM  switch back to previous setting
REM ---------------------------------

set PAX_CONSTRUCT_OPTIONS=%OLD_CONSTRUCT_OPTIONS%

REM -------------------------------------
REM  Spring Dynamic Modules sample beans
REM -------------------------------------

call pax-import-bundle -g org.springframework.osgi.samples -a weather-dao -v 1.0-rc1-SNAPSHOT
call pax-import-bundle -g org.springframework.osgi.samples -a weather-service -v 1.0-rc1-SNAPSHOT

REM ------------------------------------------------------------
REM  create new OSGi service bundle with example code and tests
REM ------------------------------------------------------------

call pax-create-bundle -p org.example.service -- -Djunit

REM --------------------------------------------------------------------
REM  create new Spring Dynamic Modules bean with example code and tests
REM --------------------------------------------------------------------

call pax-create-bundle -p org.example.bean -- -Dspring -Djunit

REM -------------------
REM  build and deploy!
REM -------------------

call mvn clean install pax:provision

