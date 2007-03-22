#!/bin/sh

SETTINGS=`dirname $0`/settings.xml

mvn -s ${SETTINGS} pax-build:create -DgroupId=simple.project -DartifactId=osgi-web-app

cd osgi-web-app

mvn install

cd bundles/wrap

mvn -s ${SETTINGS} pax-build:wrap -DgroupId=javax.servlet -DartifactId=servlet-api -Dversion=2.5

cd ../install

mvn -s ${SETTINGS} pax-build:install -DgroupId=org.ops4j.pax.logging -DartifactId=api -Dversion=0.9.4
mvn -s ${SETTINGS} pax-build:install -DgroupId=org.ops4j.pax.logging -DartifactId=jcl -Dversion=0.9.4
mvn -s ${SETTINGS} pax-build:install -DgroupId=org.ops4j.pax.logging -DartifactId=slf4j -Dversion=0.9.4

mvn -s ${SETTINGS} pax-build:install -DgroupId=org.ungoverned.osgi.bundle -DartifactId=http -Dversion=1.1.2

cd ../compile

mvn -s ${SETTINGS} pax-build:compile -Dpackage=my.osgi.code.myBundle -Dname=myBundle -Dversion=0.1.0-SNAPSHOT

cd ../..

mvn install

mvn -s ${SETTINGS} pax-build:runner -Ddeploy=true

