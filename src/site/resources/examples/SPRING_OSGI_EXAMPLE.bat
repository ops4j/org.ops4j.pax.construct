
REM --------------------------------
REM  let's start with a new project
REM --------------------------------

call pax-create-project -g examples -a spring

cd spring

REM ---------------------------------------------------------------------------
REM  importTransitive also imports any bundles spring-osgi-extender depends on
REM    (use widenScope to also consider non-provided dependencies)
REM ---------------------------------------------------------------------------

call pax-import-bundle -g org.springframework.osgi -a spring-osgi-extender -v 1.0 -- -DwidenScope -DimportTransitive

REM -----------------------------------
REM  basic SLF4J implementation bundle
REM -----------------------------------

call pax-import-bundle -g org.slf4j -a slf4j-simple -v 1.4.3

REM ------------------------------------------------------------------------------------
REM  wrapping examples: put in subdirectory and add wrapped version to the wrapper name
REM ------------------------------------------------------------------------------------

call pax-wrap-jar -a asm -v 2.2.3                        -- -DaddVersion "-DtargetDirectory=wrappers"
call pax-wrap-jar -g javax.servlet -a jsp-api -v 2.0     -- -DaddVersion "-DtargetDirectory=wrappers"
call pax-wrap-jar -g javax.servlet -a servlet-api -v 2.5 -- -DaddVersion "-DtargetDirectory=wrappers"

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

