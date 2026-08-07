#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "오류: ${ENV_FILE} 파일이 없습니다."
  echo ".env.example을 복사하여 .env를 먼저 생성하세요."
  exit 1
fi

cd "${PROJECT_ROOT}"

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

exec ./gradlew bootRun
