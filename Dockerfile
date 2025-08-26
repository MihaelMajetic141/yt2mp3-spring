FROM gradle:8.9-jdk21 AS dev

RUN apt-get update && \
    apt-get install -y python3-pip python3-venv ffmpeg && \
    python3 -m venv /venv && \
    /venv/bin/pip install --no-cache-dir yt-dlp && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /spring-app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN ./gradlew dependencies --no-daemon || true

COPY . .

CMD ["./gradlew", "bootRun", "--no-daemon"]


# Prod.
## Stage 1: Build the Spring Boot app
#FROM gradle:8.9-jdk21 AS build
#
#WORKDIR /spring-app
#
#COPY gradlew ./
#COPY gradle gradle
#COPY build.gradle.kts settings.gradle.kts ./
#
#RUN ./gradlew dependencies --no-daemon
#
#COPY . .
#
#RUN ./gradlew build --no-daemon -x test
#
#
## Switch to a Debian-based JRE for apt-get (Alpine uses apk, which doesn't have apt)
## If you prefer Alpine, replace with apk commands below
#FROM eclipse-temurin:21-jre
#
## Install Python, pip, ffmpeg, and yt-dlp
# Install Python, pip, venv, ffmpeg, and yt-dlp
#RUN apt-get update && \
#    apt-get install -y python3-pip python3-venv ffmpeg && \
#    python3 -m venv /venv && \
#    /venv/bin/pip install --no-cache-dir yt-dlp && \
#    apt-get clean && rm -rf /var/lib/apt/lists/*
#
#WORKDIR /app
#
#COPY --from=build /spring-app/build/libs/*.jar app.jar
#
#EXPOSE 8080
#
#USER 1000
#
#CMD ["java", "-jar", "app.jar"]