FROM openjdk:21-jdk-slim AS build

WORKDIR /usr/src/app

COPY . .

RUN apt-get update && apt-get install -y maven

ARG MAVEN_ARGS="--no-transfer-progress -Dstyle.color=always"
ARG MAVEN_SKIP_ARGS="-P prettierSkip -Dmaven.test.skip=true -Dmaven.source.skip=true"
RUN mvn $MAVEN_ARGS $MAVEN_SKIP_ARGS package

# Deploy image
FROM openjdk:21-jdk-slim

RUN apt-get update && apt-get install -y coreutils

WORKDIR /usr/src/app/

COPY --from=build /usr/src/app/otp-shaded/target/otp-shaded-* otp.jar

VOLUME ["/var/opentripplanner"]