# docker build -t k8s-operator:1.0 .

ARG VERSION=1.0

FROM zenika/alpine-maven:3 as build
COPY src src
COPY pom.xml pom.xml
RUN mvn package

FROM oracle/graalvm-ce:20.0.0 as native
ARG VERSION
COPY --from=build /usr/src/app/target/k8s-operator-$VERSION.jar /var/k8s-operator-$VERSION.jar
WORKDIR /opt/graalvm
RUN gu install native-image \
 && native-image -jar /var/k8s-operator-$VERSION.jar \
 && mv k8s-operator-$VERSION /opt/k8s-operator-$VERSION

FROM scratch
ARG VERSION
WORKDIR /home
COPY --from=native /opt/k8s-operator-$VERSION operator
ENTRYPOINT ["./operator"]