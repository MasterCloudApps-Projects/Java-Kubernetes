FROM openjdk:11-jre-slim
USER root
WORKDIR /
COPY target/reload-configmap-1.0.0.jar /opt/

# Random user for k8s
USER 1000300

EXPOSE 8080

ENTRYPOINT ["java","-jar","/opt/reload-configmap-1.0.0.jar"]
