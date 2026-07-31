# syntax=docker/dockerfile:1

# =========================================================
# 1. Builder
# =========================================================
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Gradle Wrapper와 빌드 설정을 먼저 복사해 의존성 레이어 캐시 활용
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

# 애플리케이션 소스 복사
COPY src ./src

# 실행 가능한 Spring Boot JAR 생성
RUN ./gradlew clean bootJar --no-daemon -x test \
    && BOOT_JAR_COUNT="$( \
        find build/libs \
            -maxdepth 1 \
            -type f \
            -name '*.jar' \
            ! -name '*-plain.jar' \
        | wc -l \
    )" \
    && test "$BOOT_JAR_COUNT" -eq 1 \
    && cp "$( \
        find build/libs \
            -maxdepth 1 \
            -type f \
            -name '*.jar' \
            ! -name '*-plain.jar' \
    )" /workspace/app.jar


# =========================================================
# 2. Runtime
# =========================================================
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# 현재 Temurin Jammy 이미지에 남아 있는 glibc 취약 버전을
# Ubuntu 보안 저장소의 수정 버전으로 업데이트합니다.
#
# 전체 apt-get upgrade는 수행하지 않고,
# CVE-2026-5450과 관련된 설치 패키지만 제한적으로 업데이트합니다.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install \
        --only-upgrade \
        --no-install-recommends \
        -y \
        libc6 \
        libc-bin \
        locales \
    && dpkg-query -W \
        -f='${binary:Package}=${Version}\n' \
        libc6 \
        libc-bin \
        locales \
    && rm -rf /var/lib/apt/lists/*

# Root가 아닌 전용 사용자로 애플리케이션 실행
RUN groupadd \
        --system \
        --gid 10001 \
        otboo \
    && useradd \
        --system \
        --uid 10001 \
        --gid otboo \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        otboo

# Builder에서 생성한 실행 JAR만 Runtime 이미지로 복사
COPY --from=builder \
    --chown=otboo:otboo \
    /workspace/app.jar \
    /app/app.jar

USER otboo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]