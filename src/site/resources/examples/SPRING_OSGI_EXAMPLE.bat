
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

call pax-add-repository -i com.springsource.repository.bundles.release -u http://repository.springsource.com/maven/bundles/release
call pax-add-repository -i com.springsource.repository.bundles.external -u http://repository.springsource.com/maven/bundles/external

call pax-import-bundle -g org.springframework.osgi -a spring-osgi-extender -v 1.1.2 -- -DimportTransitive -DwidenScope

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

