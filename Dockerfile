FROM gradle:8.14.2-jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN jlink \
    --verbose \
    --add-modules java.base,jdk.unsupported,java.desktop,java.instrument,java.logging,java.management,java.sql,java.xml \
    --compress 2 --strip-debug --no-header-files --no-man-pages \
    --output /opt/minimal-java

RUN gradle build --no-daemon -x test

FROM alpine:3

ENV JAVA_HOME=/opt/minimal-java
ENV PATH="$JAVA_HOME/bin:$PATH"

ENV CONFIG_PATH="/config"

RUN mkdir /app
# Copy the custom minimal JRE from the builder stage
COPY --from=build "$JAVA_HOME" "$JAVA_HOME"
COPY --from=build /home/gradle/src/build/libs/ /app/

ENTRYPOINT ["java","-jar", "/app/coral-server-1.0-SNAPSHOT.jar"]