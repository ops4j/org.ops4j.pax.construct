
call pax-create-project -g examples -a spring-osgi-example

cd spring-osgi-example

call pax-add-repository -i spring-osgi -u http://static.springframework.org/maven2-snapshots -- -Dsnapshots "-Dreleases=false"
call pax-add-repository -i spring -u http://s3.amazonaws.com/maven.springframework.org/milestone

set OLD_CONSTRUCT_OPTIONS=%PAX_CONSTRUCT_OPTIONS%
set PAX_CONSTRUCT_OPTIONS=-DaddVersion -DtargetDirectory=wrappers %OLD_CONSTRUCT_OPTIONS%

call pax-wrap-jar -a backport-util-concurrent -v 3.0 -- "-DimportPackage=sun.misc;resolution:=optional,*"
call pax-wrap-jar -a commons-collections -v 3.2

call pax-wrap-jar -a asm -v 2.2.3
call pax-wrap-jar -a aopalliance -v 1.0
call pax-wrap-jar -g javax.servlet -a servlet-api -v 2.5
call pax-wrap-jar -g javax.servlet -a jsp-api -v 2.0
call pax-wrap-jar -a junit

set PAX_CONSTRUCT_OPTIONS=%OLD_CONSTRUCT_OPTIONS%

call pax-import-bundle -g org.slf4j -a slf4j-simple -v 1.4.3

call pax-import-bundle -g org.springframework.osgi -a spring-osgi-extender -v 1.0-rc1-SNAPSHOT -- -DwidenScope -DimportTransitive

call pax-import-bundle -g org.springframework.osgi.samples -a weather-dao -v 1.0-rc1-SNAPSHOT
call pax-import-bundle -g org.springframework.osgi.samples -a weather-service -v 1.0-rc1-SNAPSHOT

call mvn clean install pax:provision
