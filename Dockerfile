# syntax=docker/dockerfile:1

# =========================================================
# 1. Builder Stage
# =========================================================
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Gradle Wrapper와 빌드 설정을 먼저 복사하여
# 소스 코드 변경 시에도 의존성 레이어 캐시를 활용합니다.
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Gradle 의존성을 미리 내려받습니다.
RUN ./gradlew dependencies --no-daemon

# 애플리케이션 소스를 복사합니다.
COPY src ./src

# 실행 가능한 Spring Boot JAR를 생성합니다.
#
# - 테스트는 CI에서 별도로 수행하므로 Docker 이미지 빌드에서는 제외합니다.
# - plain JAR를 제외한 실행 JAR가 정확히 하나인지 확인합니다.
# - 최종 실행 파일명을 app.jar로 통일합니다.
RUN ./gradlew clean bootJar --no-daemon -x test \
    && BOOT_JAR_COUNT="$( \
        find build/libs \
            -maxdepth 1 \
            -type f \
            -name '*.jar' \
            ! -name '*-plain.jar' \
        | wc -l \
        | tr -d ' ' \
    )" \
    && test "$BOOT_JAR_COUNT" -eq 1 \
    && BOOT_JAR="$( \
        find build/libs \
            -maxdepth 1 \
            -type f \
            -name '*.jar' \
            ! -name '*-plain.jar' \
    )" \
    && cp "$BOOT_JAR" /workspace/app.jar


# =========================================================
# 2. Runtime Stage
# =========================================================
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# ECR Basic Scan에서 확인된 수정 가능한 취약점에 대응합니다.
#
# 대응 대상:
# - CVE-2026-5450
#   - libc6
#   - libc-bin
#   - locales
#
# - CVE-2026-58469
#   - wget
#
# - CVE-2026-40355
# - CVE-2026-40356
#   - libkrb5-3
#   - libkrb5support0
#   - libgssapi-krb5-2
#   - libk5crypto3
#
# 전체 OS 패키지를 업데이트하는 apt-get upgrade는 수행하지 않습니다.
# 확인된 취약 패키지만 --only-upgrade로 제한적으로 업데이트합니다.
#
# 패키지 업데이트 후 최소 보안 수정 버전을 검사하며,
# 버전이 기준에 미달하면 Docker 이미지 빌드를 실패시킵니다.
# =========================================================
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install \
        --only-upgrade \
        --no-install-recommends \
        -y \
        libc6 \
        libc-bin \
        locales \
        wget \
        libkrb5-3 \
        libkrb5support0 \
        libgssapi-krb5-2 \
        libk5crypto3 \
    && WGET_VERSION="$(dpkg-query -W -f='${Version}' wget)" \
    && KRB5_VERSION="$(dpkg-query -W -f='${Version}' libkrb5-3)" \
    && dpkg --compare-versions \
        "$WGET_VERSION" ge "1.21.2-2ubuntu1.3" \
    && dpkg --compare-versions \
        "$KRB5_VERSION" ge "1.19.2-2ubuntu0.8" \
    && dpkg-query -W \
        -f='${binary:Package}=${Version}\n' \
        libc6 \
        libc-bin \
        locales \
        wget \
        libkrb5-3 \
        libkrb5support0 \
        libgssapi-krb5-2 \
        libk5crypto3 \
    && rm -rf /var/lib/apt/lists/*


# 애플리케이션을 Root가 아닌 전용 사용자로 실행합니다.
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

# Builder Stage에서 생성한 실행 JAR만 Runtime 이미지로 복사합니다.
COPY --from=builder \
    --chown=otboo:otboo \
    /workspace/app.jar \
    /app/app.jar

USER otboo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]