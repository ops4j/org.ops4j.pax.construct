mvn osgi-project:create -DgroupId=simple.project -DartifactId=osgi.web.app

cd osgi-web-app
mvn install

cd bundles
cd wrap

mvn osgi-project:wrap -DgroupId=javax.servlet -DartifactId=servlet-api -Dversion=2.5

cd ..
cd install

mvn osgi-project:install -DgroupId=org.ops4j.pax.logging -DartifactId=api -Dversion=0.9.4
mvn osgi-project:install -DgroupId=org.ops4j.pax.logging -DartifactId=jcl -Dversion=0.9.4
mvn osgi-project:install -DgroupId=org.ops4j.pax.logging -DartifactId=slf4j -Dversion=0.9.4

mvn osgi-project:install -DgroupId=org.ungoverned.osgi.bundle -DartifactId=http -Dversion=1.1.2

cd ..
cd compile

mvn osgi-project:compile -DgroupId=my.osgi.code -DartifactId=myBundle -Dversion=0.1.0-SNAPSHOT

cd ..

