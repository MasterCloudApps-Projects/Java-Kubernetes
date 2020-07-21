FROM openjdk:14-alpine
COPY target/healthcheck-*.jar healthcheck.jar
EXPOSE 8080
USER 1000300
CMD ["java", "-Dcom.sun.management.jmxremote", "-Xmx128m", "-jar", "healthcheck.jar"]