
mvn org.ops4j.pax.construct:maven-pax-plugin:create-project -DgroupId=simple.project -DartifactId=osgi-web-app

cd osgi-web-app

mvn install

mvn pax:wrap-jar -DgroupId=javax.servlet -DartifactId=servlet-api -Dversion=2.5
mvn pax:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=api -Dversion=0.9.4
mvn pax:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=jcl -Dversion=0.9.4
mvn pax:import-bundle -DgroupId=org.ops4j.pax.logging -DartifactId=slf4j -Dversion=0.9.4
mvn pax:import-bundle -DgroupId=org.ungoverned.osgi.bundle -DartifactId=http -Dversion=1.1.2
mvn pax:create-bundle -Dpackage=my.osgi.code.myBundle -Dname=myBundle -Dversion=0.1.0-SNAPSHOT

mvn install pax:provision

