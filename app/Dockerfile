FROM tomcat:8.0
MAINTAINER Harm Pauw <h.pauw@prowareness.nl>

RUN ["rm", "-fr", "/usr/local/tomcat/webapps/ROOT"]
ADD target/petclinic.war /usr/local/tomcat/webapps/ROOT.war

CMD ["catalina.sh", "run"]
