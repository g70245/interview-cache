# build stage
FROM gradle:7.6.0-jdk17 AS builder

WORKDIR /builder
COPY . .

RUN --mount=type=cache,target=/home/gradle/.gradle gradle clean test copyReport bootJar -i --stacktrace --no-daemon

# final stage
FROM openjdk:17

WORKDIR /app

COPY --from=builder /builder/build/libs/app.jar .

EXPOSE 8080

ENTRYPOINT [ "java", "-jar", "app.jar" ]
