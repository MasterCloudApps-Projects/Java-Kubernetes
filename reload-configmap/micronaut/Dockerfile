FROM openjdk:14-alpine
COPY target/reload-configmap-*.jar reload-configmap.jar
EXPOSE 8080
USER 1000300
CMD ["java", "-Dcom.sun.management.jmxremote", "-Xmx128m", "-jar", "reload-configmap.jar"]