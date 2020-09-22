FROM openjdk:14-alpine
COPY target/metrics-*.jar metrics.jar
EXPOSE 8080
CMD ["java", "-Dcom.sun.management.jmxremote", "-Xmx128m", "-jar", "metrics.jar"]