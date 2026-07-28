# syntax=docker/dockerfile:1

# 1단계: Spring Boot JAR 빌드
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Gradle 의존성 캐시를 활용할 수 있도록 빌드 설정을 먼저 복사
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# 애플리케이션 소스 복사 및 실행 가능한 JAR 생성
COPY src ./src

RUN ./gradlew clean bootJar --no-daemon -x test \
    && cp build/libs/*.jar app.jar


# 2단계: 애플리케이션 실행
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# 애플리케이션 실행용 비루트 사용자 생성
RUN groupadd --system --gid 10001 otboo \
    && useradd \
        --system \
        --uid 10001 \
        --gid otboo \
        --no-create-home \
        --shell /usr/sbin/nologin \
        otboo

# 빌드 결과물만 실행 이미지에 복사
COPY --from=builder --chown=otboo:otboo /workspace/app.jar /app/app.jar

USER otboo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]