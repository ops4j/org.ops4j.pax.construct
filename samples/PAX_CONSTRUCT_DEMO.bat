
call pax-create-project -g simple.project -a osgi-web-app

cd osgi-web-app

call pax-wrap-jar -g javax.servlet -a servlet-api -v 2.5
call pax-import-bundle -g org.ops4j.pax.logging -a api -v 0.9.4
call pax-import-bundle -g org.ops4j.pax.logging -a jcl -v 0.9.4
call pax-import-bundle -g org.ops4j.pax.logging -a slf4j -v 0.9.4
call pax-import-bundle -g org.ungoverned.osgi.bundle -a http -v 1.1.2
call pax-create-bundle -p my.osgi.code.myBundle -n myBundle

call mvn install pax:provision

