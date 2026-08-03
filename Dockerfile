# syntax=docker/dockerfile:1

# =========================================================
# 1. Builder Stage
# =========================================================

# 동일한 Git Commit을 다시 빌드할 때 베이스 이미지가 임의로 바뀌지 않도록
# linux/amd64용 Eclipse Temurin JDK 17 Jammy Manifest Digest를 고정합니다.
FROM eclipse-temurin:17-jdk-jammy@sha256:61a9a7bd265855cb9ebdd396f5925b16dbdde01feb9b97a87f7f6008fc39c2b4 AS builder

WORKDIR /workspace

# Gradle Wrapper와 빌드 설정을 먼저 복사하여
# 소스 코드 변경 시에도 의존성 레이어 캐시를 활용합니다.
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Gradle 의존성을 미리 확인합니다.
RUN ./gradlew dependencies --no-daemon

# 애플리케이션 소스를 복사합니다.
COPY src ./src

# 실행 가능한 Spring Boot JAR를 생성합니다.
#
# - 테스트는 CI에서 별도로 수행하므로 이미지 빌드에서는 제외합니다.
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

# linux/amd64용 Eclipse Temurin JRE 17 Jammy Manifest Digest를 고정합니다.
FROM eclipse-temurin:17-jre-jammy@sha256:37aa8b6bb46ece31288bc9c21b39ab6cd2a923abd25b70690c6a9003ff97f948 AS runtime

WORKDIR /app

# 이미지 태그를 생성한 원본 Git Commit을 이미지 Label에도 기록합니다.
# 빌드 시 --build-arg IMAGE_SOURCE_COMMIT=<full SHA>가 반드시 필요합니다.
ARG IMAGE_SOURCE_COMMIT
RUN test -n "$IMAGE_SOURCE_COMMIT"
LABEL org.opencontainers.image.revision="$IMAGE_SOURCE_COMMIT"

# =========================================================
# Runtime OS 패키지 보안 업데이트
# =========================================================
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
# 전체 OS 패키지를 일괄 변경하는 apt-get upgrade는 수행하지 않습니다.
# 확인된 취약 패키지만 --only-upgrade로 제한적으로 업데이트합니다.
#
# 설치 대상 8개 패키지를 모두 개별 검증합니다.
# 하나라도 최소 보안 수정 버전에 미달하면 빌드를 실패시킵니다.
RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install \
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
        libk5crypto3; \
    check_min_version() { \
        package_name="$1"; \
        minimum_version="$2"; \
        installed_version="$(dpkg-query -W -f='${Version}' "$package_name")"; \
        echo "Verifying ${package_name}: installed=${installed_version}, minimum=${minimum_version}"; \
        dpkg --compare-versions "$installed_version" ge "$minimum_version"; \
    }; \
    check_min_version libc6 "2.35-0ubuntu3.14"; \
    check_min_version libc-bin "2.35-0ubuntu3.14"; \
    check_min_version locales "2.35-0ubuntu3.14"; \
    check_min_version wget "1.21.2-2ubuntu1.3"; \
    check_min_version libkrb5-3 "1.19.2-2ubuntu0.8"; \
    check_min_version libkrb5support0 "1.19.2-2ubuntu0.8"; \
    check_min_version libgssapi-krb5-2 "1.19.2-2ubuntu0.8"; \
    check_min_version libk5crypto3 "1.19.2-2ubuntu0.8"; \
    dpkg-query -W \
        -f='${binary:Package}=${Version}\n' \
        libc6 \
        libc-bin \
        locales \
        wget \
        libkrb5-3 \
        libkrb5support0 \
        libgssapi-krb5-2 \
        libk5crypto3; \
    rm -rf /var/lib/apt/lists/*

# 애플리케이션을 Root가 아닌 전용 사용자로 실행합니다.
#
# 사용자 생성 후 /app 디렉터리 자체의 소유권도 otboo로 변경합니다.
RUN groupadd \
        --system \
        --gid 10001 \
        otboo \
    && useradd \
        --system \
        --uid 10001 \
        --gid otboo \
        --no-create-home \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        otboo \
    && chown -R otboo:otboo /app

# Builder Stage에서 생성한 실행 JAR를 한 번만 복사합니다.
COPY --from=builder \
    --chown=otboo:otboo \
    /workspace/app.jar \
    /app/app.jar

USER otboo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
