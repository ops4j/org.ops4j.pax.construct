#!/bin/sh

SETTINGS=`dirname $0`/settings.xml

mvn -s ${SETTINGS} pax-build:create-project -DgroupId=simple.project -DartifactId=osgi-web-app

cd osgi-web-app

mvn install

cd bundles/wrappers

mvn -s ${SETTINGS} pax-build:wrap-jar -DgroupId=javax.servlet -DartifactId=servlet-api -Dversion=2.5

cd ../imported

mvn -s ${SETTINGS} pax-build:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=api -Dversion=0.9.4
mvn -s ${SETTINGS} pax-build:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=jcl -Dversion=0.9.4
mvn -s ${SETTINGS} pax-build:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=slf4j -Dversion=0.9.4

mvn -s ${SETTINGS} pax-build:import-bundle -DgroupId=org.ungoverned.osgi.bundle -DartifactId=http -Dversion=1.1.2

cd ../compiled

mvn -s ${SETTINGS} pax-build:create-bundle -Dpackage=my.osgi.code.myBundle -Dname=myBundle -Dversion=0.1.0-SNAPSHOT

cd ../..

mvn install

mvn -s ${SETTINGS} pax-build:provision

