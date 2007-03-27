
mvn org.ops4j.pax.build:maven-pax-build-plugin:create-project -DgroupId=simple.project -DartifactId=osgi-web-app

cd osgi-web-app

mvn install

mvn pax-build:wrap-jar -DgroupId=javax.servlet -DartifactId=servlet-api -Dversion=2.5
mvn pax-build:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=api -Dversion=0.9.4
mvn pax-build:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=jcl -Dversion=0.9.4
mvn pax-build:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=slf4j -Dversion=0.9.4
mvn pax-build:import-bundle -DgroupId=org.ungoverned.osgi.bundle -DartifactId=http -Dversion=1.1.2
mvn pax-build:create-bundle -Dpackage=my.osgi.code.myBundle -Dname=myBundle -Dversion=0.1.0-SNAPSHOT

mvn install pax-build:provision

