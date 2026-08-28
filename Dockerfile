# syntax=docker/dockerfile:1

# =========================================================
# 1. Builder Stage
# =========================================================

FROM eclipse-temurin:17-jdk-jammy@sha256:61a9a7bd265855cb9ebdd396f5925b16dbdde01feb9b97a87f7f6008fc39c2b4 AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

COPY src ./src

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

FROM eclipse-temurin:17-jre-jammy@sha256:37aa8b6bb46ece31288bc9c21b39ab6cd2a923abd25b70690c6a9003ff97f948 AS runtime

WORKDIR /app

ARG IMAGE_SOURCE_COMMIT

RUN test -n "$IMAGE_SOURCE_COMMIT"

LABEL org.opencontainers.image.revision="$IMAGE_SOURCE_COMMIT"

# =========================================================
# Runtime OS 패키지 보안 조치
# =========================================================
#
# 애플리케이션과 Health Check에서 사용하지 않는 wget을 제거합니다.
#
# glibc 및 Kerberos 관련 패키지는 확인된 최소 보안 버전 이상으로
# 제한적으로 업데이트하고, 각 패키지의 버전을 개별 검증합니다.

RUN set -eux; \
    apt-get update; \
    apt-get purge \
        -y \
        wget; \
    DEBIAN_FRONTEND=noninteractive apt-get install \
        --only-upgrade \
        --no-install-recommends \
        -y \
        libc6 \
        libc-bin \
        locales \
        libkrb5-3 \
        libkrb5support0 \
        libgssapi-krb5-2 \
        libk5crypto3; \
    check_min_version() { \
        package_name="$1"; \
        minimum_version="$2"; \
        installed_version="$( \
            dpkg-query \
                -W \
                -f='${Version}' \
                "$package_name" \
        )"; \
        echo "Verifying ${package_name}: installed=${installed_version}, minimum=${minimum_version}"; \
        dpkg --compare-versions \
            "$installed_version" \
            ge \
            "$minimum_version"; \
    }; \
    check_min_version libc6 "2.35-0ubuntu3.14"; \
    check_min_version libc-bin "2.35-0ubuntu3.14"; \
    check_min_version locales "2.35-0ubuntu3.14"; \
    check_min_version libkrb5-3 "1.19.2-2ubuntu0.8"; \
    check_min_version libkrb5support0 "1.19.2-2ubuntu0.8"; \
    check_min_version libgssapi-krb5-2 "1.19.2-2ubuntu0.8"; \
    check_min_version libk5crypto3 "1.19.2-2ubuntu0.8"; \
    if command -v wget >/dev/null 2>&1; then \
        echo "wget is still installed"; \
        exit 1; \
    fi; \
    dpkg-query \
        -W \
        -f='${binary:Package}=${Version}\n' \
        libc6 \
        libc-bin \
        locales \
        libkrb5-3 \
        libkrb5support0 \
        libgssapi-krb5-2 \
        libk5crypto3; \
    rm -rf /var/lib/apt/lists/*

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

COPY --from=builder \
    --chown=otboo:otboo \
    /workspace/app.jar \
    /app/app.jar

USER otboo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]