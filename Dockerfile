FROM gradle:8.9-jdk21 AS dev

WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN ./gradlew dependencies --no-daemon || true

COPY . .

CMD ["./gradlew", "bootRun", "--no-daemon"]