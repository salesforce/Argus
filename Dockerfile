FROM tomcat
ENV JAVA_OPTS -Dcatalina.base="$CATALINA_HOME" -Dcatalina.home="$CATALINA_HOME" -Dwtp.deploy="$CATALINA_HOME/webapps" -Djava.endorsed.dirs="$CATALINA_HOME/endorsed" -Xms1024m -Xmx8192m -XX:MaxPermSize=128m -Dargus.config.private.location="$CATALINA_HOME/argus_config/server.config"
RUN CATALINA_OPTS="-Dargus.config.public.location=/Users/{userName}/Argus/properties/server.config"
RUN CATALINA_OPTS="$CATALINA_OPTS $JPDA_OPTS"
ADD ./ArgusWebServices/target/argus-webservices-4.0-SNAPSHOT.war $CATALINA_HOME/webapps/argusws.war
ADD ./ArgusWeb/app $CATALINA_HOME/webapps/app
