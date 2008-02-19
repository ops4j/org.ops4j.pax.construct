
REM --------------------------------
REM  let's start with a new project
REM --------------------------------

call pax-create-project -g examples -a spring

cd spring

REM -----------------------------------------------------------------------------------
REM  first import the Spring Dynamic-Modules Extender bundle - we use importTransitive
REM  to also import any bundles it depends on and widenScope to do an exhaustive check
REM  of all dependencies (normally only "provided" scope dependencies are checked)
REM -----------------------------------------------------------------------------------

call pax-import-bundle -g org.springframework.osgi -a spring-osgi-extender -v 1.0 -- -DimportTransitive -DwidenScope

REM -------------------------------------------------------------
REM  grab the basic SLF4J implementation bundle for this example
REM -------------------------------------------------------------

call pax-import-bundle -g org.slf4j -a slf4j-simple -v 1.4.3

REM -----------------------------------------------------------------------------------------------
REM  we also need some additional libraries wrapped as bundles - let's put these in a subdirectory
REM -----------------------------------------------------------------------------------------------

call pax-create-module -a wrappers
cd wrappers

call pax-wrap-jar -a asm -v 2.2.3
call pax-wrap-jar -a aopalliance -v 1.0

call pax-wrap-jar -g javax.servlet -a jsp-api -v 2.0
call pax-wrap-jar -g javax.servlet -a servlet-api -v 2.5

REM ----------------------------------------------------------------------------------------------
REM  this bundle is used with 1.4 JVMs, it uses a customized import to treat sun.misc as optional
REM ----------------------------------------------------------------------------------------------

call pax-wrap-jar -a backport-util-concurrent -v 3.0 -- "-DimportPackage=sun.misc;resolution:=optional,*"

cd ..

REM ------------------------------------------------------------
REM  create new OSGi service bundle with example code and tests
REM ------------------------------------------------------------

call pax-create-bundle -p org.example.service -- -Djunit

REM --------------------------------------------------------------------
REM  create new Spring Dynamic Modules bean with example code and tests
REM --------------------------------------------------------------------

call pax-create-bundle -p org.example.bean -- -Dspring -Djunit

REM ----------------------------
REM  finally, build and deploy!
REM ----------------------------

call mvn clean install pax:provision

