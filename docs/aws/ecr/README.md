# Amazon ECR 구성 및 이미지 검증

## 1. 작업 범위

Issue #46에서는 ECS에서 실행할 Docker 이미지를 저장하기 위한
Amazon ECR Private Repository를 구성하고 다음 항목을 검증합니다.

* ECR Private Repository 설정
* Repository 공통 태그 적용
* Git Commit 기반 이미지 태그 생성
* `linux/amd64` 이미지 빌드
* 비루트 사용자 실행
* 취약점 스캔
* 이미지 태그 불변성
* Push·Pull 이미지 Digest 일치
* Lifecycle Policy 적용

Issue #46에서는 수동 ECR 빌드·Push를 검증했으며,
GitHub Actions 기반 자동 ECR Push는 Issue #124에서 추가 구성했습니다.
ECS 자동 배포는 후속 이슈에서 진행합니다.

## 2. ECR Repository 구성

ECR Repository는 서울 리전에 다음과 같이 구성했습니다.

```text
Region: ap-northeast-2
Repository: otboo/backend
Repository 유형: Private
Image Tag Mutability: IMMUTABLE
Mutability 예외: 없음
암호화 방식: AES256
Registry Scan Type: BASIC
Scan Frequency: SCAN_ON_PUSH
```

동일한 태그를 다른 이미지로 덮어쓰지 못하도록
Image Tag Mutability를 `IMMUTABLE`로 설정했습니다.

새 이미지가 Push될 때 취약점 스캔이 실행되도록
`SCAN_ON_PUSH`를 적용했습니다.

## 3. Repository 태그

ECR Repository에는 다음 공통 태그를 적용합니다.

```text
Project=otboo
Environment=shared
Owner=team3
ManagedBy=manual
Purpose=container-registry
```

AWS 계정 ID, Repository ARN 및 전체 Repository URI는
공개 문서에 기록하지 않습니다.

## 4. Docker 이미지 기준

### 베이스 이미지

Builder와 Runtime Stage에는 Eclipse Temurin Java 17 Jammy 이미지를 사용합니다.

동일한 Tag에서 베이스 이미지가 임의로 변경되는 것을 줄이기 위해
Dockerfile에는 Tag와 Digest를 함께 지정합니다.

베이스 이미지 Digest를 변경한 경우에는
이미지 빌드와 ECR 취약점 스캔을 다시 수행합니다.

### Runtime 이미지

Runtime 이미지는 다음 기준을 따릅니다.

* Root가 아닌 `otboo` 사용자로 실행
* `/app` 및 `/app/app.jar`의 소유자를 `otboo`로 설정
* Java 17 실행 환경 포함
* Java 컴파일러 `javac` 제외
* 불필요한 `wget` 패키지 제외
* AWS 인증정보 미포함

### 이미지 원본 Commit

이미지에는 빌드 원본 Git Commit을 다음 OCI Label로 기록합니다.

```text
org.opencontainers.image.revision
```

이미지 태그와 OCI Label은 동일한 Git Commit을 기준으로 생성합니다.

## 5. 이미지 태그 규칙

수동 검증 이미지에는 Git Commit Short SHA와 Architecture를 포함합니다.

```text
manual-{Git Commit Short SHA 7자리}-amd64
```

예시:

```text
manual-abcdef1-amd64
```

운영 기준:

* `latest` 태그를 사용하지 않습니다.
* Dockerfile 또는 애플리케이션 코드 변경 후 먼저 Commit합니다.
* 커밋되지 않은 빌드 관련 변경사항이 있으면 이미지를 빌드하지 않습니다.
* 하나의 SHA와 Architecture 조합은 하나의 변경 불가능한 이미지를 의미합니다.
* 문서만 변경된 경우 기존 이미지를 다시 빌드하지 않습니다.

## 6. 이미지 빌드

### 빌드 대상 변경사항 확인

이미지 태그의 Commit과 실제 빌드 내용이 일치하도록
빌드 전에 이미지에 영향을 주는 파일의 변경 여부를 확인합니다.

```bash
IMAGE_CONTEXT_CHANGES="$(
  git status --porcelain -- \
    Dockerfile \
    gradlew \
    gradle \
    build.gradle \
    build.gradle.kts \
    settings.gradle \
    settings.gradle.kts \
    gradle.properties \
    src
)"

if [ -n "$IMAGE_CONTEXT_CHANGES" ]; then
  echo "이미지 빌드에 영향을 주는 커밋되지 않은 변경사항이 있습니다."
  printf '%s\n' "$IMAGE_CONTEXT_CHANGES"
  exit 1
fi
```

### 빌드 원본 Commit 고정

이미지 원본 Commit은 빌드 시작 시 한 번만 확정합니다.

확정한 Commit과 이미지 태그는 빌드 인자, OCI Label 및
ECR Push 단계에서 동일하게 재사용합니다.

```bash
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"
ECR_REPOSITORY="otboo/backend"
BUILD_METADATA_FILE="build/ecr-image.env"

IMAGE_SOURCE_COMMIT="$(git rev-parse HEAD)"
IMAGE_SOURCE_SHA="$(git rev-parse --short=7 "$IMAGE_SOURCE_COMMIT")"
IMAGE_TAG="manual-${IMAGE_SOURCE_SHA}-amd64"
LOCAL_IMAGE="otboo:${IMAGE_TAG}"

mkdir -p "$(dirname "$BUILD_METADATA_FILE")"

cat > "$BUILD_METADATA_FILE" <<EOF
IMAGE_SOURCE_COMMIT=$IMAGE_SOURCE_COMMIT
IMAGE_SOURCE_SHA=$IMAGE_SOURCE_SHA
IMAGE_TAG=$IMAGE_TAG
LOCAL_IMAGE=$LOCAL_IMAGE
EOF
```

`build/ecr-image.env`에는 인증정보가 아닌
이미지 원본 Commit과 태그만 저장합니다.

Push 단계에서는 현재 `HEAD`를 다시 계산하지 않고
빌드 시점에 저장한 값을 불러와 사용합니다.

### 이미지 빌드

ECR Basic Scan이 지원하는 단일 이미지 Manifest를 생성하기 위해
수동 검증 빌드에서는 기본 Provenance Attestation을 비활성화합니다.

```bash
docker buildx build \
  --platform linux/amd64 \
  --provenance=false \
  --build-arg IMAGE_SOURCE_COMMIT="$IMAGE_SOURCE_COMMIT" \
  --tag "$LOCAL_IMAGE" \
  --load \
  .
```

Docker Buildx의 기본 Provenance Attestation이 포함되면
이미지가 OCI Image Index 형식으로 Push될 수 있습니다.

ECR Basic Scan에서는 OCI Image Index를 스캔하지 못하므로
이번 수동 검증에서는 단일 이미지 Manifest를 생성합니다.

GitHub Actions 자동 Push에서도 ECR Basic Scan과 단일 이미지 Manifest 기준을 유지하기 위해
기본 Provenance Attestation을 비활성화합니다.

SBOM과 Provenance는 후속 공급망 보안 점검에서
ECR 스캔 방식과 공급망 메타데이터 관리 방식을 함께 검토한 뒤 적용합니다.

## 7. 이미지 Push

### 빌드 메타데이터 확인

Push 단계에서는 빌드 시점에 저장한 원본 Commit과 태그를 불러옵니다.

```bash
BUILD_METADATA_FILE="build/ecr-image.env"

test -f "$BUILD_METADATA_FILE" || {
  echo "빌드 메타데이터 파일이 없습니다."
  exit 1
}

. "$BUILD_METADATA_FILE"

IMAGE_LABEL_COMMIT="$(
  docker image inspect "$LOCAL_IMAGE" \
    --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
)"

test "$IMAGE_LABEL_COMMIT" = "$IMAGE_SOURCE_COMMIT" || {
  echo "이미지 OCI Label과 빌드 원본 Commit이 일치하지 않습니다."
  exit 1
}
```

### AWS 인증 주체 확인

수동 ECR 작업은 `aws login`으로 인증한
`otboo` Profile을 기준으로 수행합니다.

정적 Access Key가 저장된 Profile이나 Root 계정 인증은 사용하지 않습니다.

```bash
AWS_CONFIG_OUTPUT="$(aws configure list --profile "$AWS_PROFILE")"

if printf '%s\n' "$AWS_CONFIG_OUTPUT" \
  | grep -Eq 'access_key[[:space:]].*shared-credentials-file'; then
  echo "정적 Access Key가 저장된 Profile은 사용할 수 없습니다."
  exit 1
fi

CALLER_ARN="$(
  aws sts get-caller-identity \
    --profile "$AWS_PROFILE" \
    --query 'Arn' \
    --output text \
    --no-cli-pager
)"

case "$CALLER_ARN" in
  arn:aws:iam::*:root)
    echo "Root 계정 인증으로는 ECR 작업을 수행할 수 없습니다."
    exit 1
    ;;
esac
```

### ECR 주소 조회

Repository URI는 AWS CLI를 통해 조회하며
문서에 직접 기록하지 않습니다.

```bash
ECR_REPOSITORY_URI="$(
  aws ecr describe-repositories \
    --repository-names "$ECR_REPOSITORY" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'repositories[0].repositoryUri' \
    --output text \
    --no-cli-pager
)"

ECR_REGISTRY="${ECR_REPOSITORY_URI%%/*}"
ECR_IMAGE="${ECR_REPOSITORY_URI}:${IMAGE_TAG}"
```

### ECR 로그인 및 Push

ECR 인증 비밀번호는 AWS CLI를 통해 임시로 전달하며
README나 명령 인자에 직접 작성하지 않습니다.

```bash
aws ecr get-login-password \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  | docker login \
      --username AWS \
      --password-stdin "$ECR_REGISTRY"

docker tag "$LOCAL_IMAGE" "$ECR_IMAGE"
docker push "$ECR_IMAGE"
```

## 8. 취약점 대응

ECR Basic Scan에서 확인된 수정 가능한 CRITICAL 및 HIGH 취약점은
패키지의 사용 여부와 수정 버전을 확인한 뒤 대응합니다.

전체 OS 패키지를 일괄 변경하는 `apt-get upgrade`는 사용하지 않습니다.

| 구분 | 대상 패키지 | 대응 방식 |
| --- | --- | --- |
| glibc | `libc6`, `libc-bin`, `locales` | `2.35-0ubuntu3.14` 이상으로 업데이트 |
| Wget | `wget` | Runtime에서 사용하지 않아 제거 |
| Kerberos | `libkrb5-3` 외 관련 패키지 | `1.19.2-2ubuntu0.8` 이상으로 업데이트 |

Dockerfile에서는 glibc 및 Kerberos 관련 패키지 7개의 버전을 확인하며,
최소 보안 수정 버전에 미달하면 이미지 빌드를 실패시킵니다.

Runtime 이미지에 `wget` 명령이 남아 있는 경우에도
이미지 빌드를 실패시킵니다.

대응 대상 취약점:

```text
CVE-2026-5450
CVE-2026-58469
CVE-2026-40355
CVE-2026-40356
```

## 9. 이미지 형식 및 스캔 검증

### 이미지 형식 확인

Push된 이미지의 Media Type이 ECR Basic Scan을 지원하는
단일 이미지 Manifest인지 확인합니다.

```bash
IMAGE_MEDIA_TYPE="$(
  aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'imageDetails[0].imageManifestMediaType' \
    --output text \
    --no-cli-pager
)"

case "$IMAGE_MEDIA_TYPE" in
  application/vnd.oci.image.manifest.v1+json|\
  application/vnd.docker.distribution.manifest.v2+json)
    ;;
  *)
    echo "ECR Basic Scan을 지원하지 않는 이미지 형식입니다: $IMAGE_MEDIA_TYPE"
    exit 1
    ;;
esac
```

지원 대상:

```text
application/vnd.oci.image.manifest.v1+json
application/vnd.docker.distribution.manifest.v2+json
```

스캔 대상에서 제외하는 형식:

```text
application/vnd.oci.image.index.v1+json
```

### 이미지 Digest 확인

```bash
IMAGE_DIGEST="$(
  aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'imageDetails[0].imageDigest' \
    --output text \
    --no-cli-pager
)"

test -n "$IMAGE_DIGEST"
test "$IMAGE_DIGEST" != "None"
```

### 스캔 완료 대기

```bash
aws ecr wait image-scan-complete \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageDigest="$IMAGE_DIGEST" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

### 스캔 결과 검증

스캔 상태와 취약점 수를 출력만 하지 않고,
필수 조건을 만족하지 못하면 검증을 실패시킵니다.

```bash
SCAN_RESULT="$(
  aws ecr describe-image-scan-findings \
    --repository-name "$ECR_REPOSITORY" \
    --image-id imageDigest="$IMAGE_DIGEST" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --output json \
    --no-cli-pager
)"

printf '%s\n' "$SCAN_RESULT" | jq -e '
  .imageScanStatus.status == "COMPLETE"
  and (.imageScanFindings.imageScanCompletedAt != null)
  and (.imageId.imageDigest != null)
  and ((.imageScanFindings.findingSeverityCounts.CRITICAL // 0) == 0)
  and ((.imageScanFindings.findingSeverityCounts.HIGH // 0) == 0)
' >/dev/null

for CVE in \
  CVE-2026-5450 \
  CVE-2026-58469 \
  CVE-2026-40355 \
  CVE-2026-40356
do
  printf '%s\n' "$SCAN_RESULT" \
    | jq -e \
        --arg cve "$CVE" \
        '[.imageScanFindings.findings[]? | select(.name == $cve)] | length == 0' \
        >/dev/null
done

printf '%s\n' "$SCAN_RESULT" | jq '{
  Tag: .imageId.imageTag,
  Digest: .imageId.imageDigest,
  Status: .imageScanStatus.status,
  CompletedAt: .imageScanFindings.imageScanCompletedAt,
  Findings: .imageScanFindings.findingSeverityCounts
}'
```

검증 성공 조건:

* 스캔 상태가 `COMPLETE`
* Image Digest가 정상적으로 조회됨
* 스캔 완료 시각이 정상적으로 조회됨
* CRITICAL 취약점 0건
* HIGH 취약점 0건
* 대응 대상 CVE 4종이 스캔 결과에 남아 있지 않음

## 10. Pull 및 Runtime 이미지 검증

### ECR 이미지 재Pull

로컬 ECR 태그 이미지를 삭제한 뒤
ECR에서 동일한 이미지를 다시 Pull합니다.

```bash
docker image rm "$ECR_IMAGE" >/dev/null 2>&1 || true
docker pull --platform linux/amd64 "$ECR_IMAGE"
```

### Push·Pull Digest 비교

ECR Image Digest와 Pull 이미지의 RepoDigest를
`sha256:...` 형식으로 정규화하여 비교합니다.

```bash
PULLED_DIGEST="$(
  docker image inspect "$ECR_IMAGE" \
    --format '{{range .RepoDigests}}{{println .}}{{end}}' \
  | grep "^${ECR_REPOSITORY_URI}@" \
  | head -n 1 \
  | cut -d '@' -f 2
)"

test "$IMAGE_DIGEST" = "$PULLED_DIGEST" || {
  echo "ECR Digest와 Pull 이미지 Digest가 일치하지 않습니다."
  exit 1
}
```

### Runtime 구성 검증

최종 Pull 이미지를 기준으로 OS, Architecture, 실행 사용자 및
OCI Label을 검증합니다.

```bash
test "$(docker image inspect "$ECR_IMAGE" --format '{{.Os}}')" = "linux"
test "$(docker image inspect "$ECR_IMAGE" --format '{{.Architecture}}')" = "amd64"
test "$(docker image inspect "$ECR_IMAGE" --format '{{.Config.User}}')" = "otboo"

PULLED_LABEL_COMMIT="$(
  docker image inspect "$ECR_IMAGE" \
    --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
)"

test "$PULLED_LABEL_COMMIT" = "$IMAGE_SOURCE_COMMIT"
```

컨테이너 내부 구성도 함께 검증합니다.

```bash
docker run --rm \
  --entrypoint sh \
  "$ECR_IMAGE" \
  -c '
    set -eu

    test "$(id -un)" = "otboo"
    test -f /app/app.jar
    test "$(stat -c "%U" /app)" = "otboo"
    test "$(stat -c "%U" /app/app.jar)" = "otboo"

    java -version 2>&1 | grep -q "17"

    ! command -v javac
    ! command -v wget

    check_min_version() {
      package_name="$1"
      minimum_version="$2"
      installed_version="$(dpkg-query -W -f="${Version}" "$package_name")"

      dpkg --compare-versions \
        "$installed_version" \
        ge \
        "$minimum_version"
    }

    check_min_version libc6 "2.35-0ubuntu3.14"
    check_min_version libc-bin "2.35-0ubuntu3.14"
    check_min_version locales "2.35-0ubuntu3.14"
    check_min_version libkrb5-3 "1.19.2-2ubuntu0.8"
    check_min_version libkrb5support0 "1.19.2-2ubuntu0.8"
    check_min_version libgssapi-krb5-2 "1.19.2-2ubuntu0.8"
    check_min_version libk5crypto3 "1.19.2-2ubuntu0.8"

    echo "Runtime 이미지 검증 완료"
  '
```

## 11. 이미지 태그 불변성 검증

원본과 Digest가 다른 임시 이미지를 동일한 태그로 Push하여
`IMMUTABLE` 설정으로 Push가 차단되는지 확인합니다.

```bash
ORIGINAL_REMOTE_DIGEST="$(
  aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'imageDetails[0].imageDigest' \
    --output text \
    --no-cli-pager
)"

TEMP_IMAGE="otboo:ecr-immutability-test-$$"

printf 'FROM %s\nLABEL ecr.immutability-test="%s"\n' \
  "$LOCAL_IMAGE" \
  "$(date -u +%Y%m%dT%H%M%SZ)" \
  | docker build \
      --platform linux/amd64 \
      --tag "$TEMP_IMAGE" \
      -

docker tag "$TEMP_IMAGE" "$ECR_IMAGE"

set +e

IMMUTABILITY_OUTPUT="$(
  docker push "$ECR_IMAGE" 2>&1
)"

IMMUTABILITY_STATUS=$?

set -e

printf '%s\n' "$IMMUTABILITY_OUTPUT"

test "$IMMUTABILITY_STATUS" -ne 0
printf '%s\n' "$IMMUTABILITY_OUTPUT" \
  | grep -q 'ImageTagAlreadyExistsException'

AFTER_REMOTE_DIGEST="$(
  aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'imageDetails[0].imageDigest' \
    --output text \
    --no-cli-pager
)"

test "$ORIGINAL_REMOTE_DIGEST" = "$AFTER_REMOTE_DIGEST"

docker image rm "$ECR_IMAGE" >/dev/null 2>&1 || true
docker tag "$LOCAL_IMAGE" "$ECR_IMAGE"
docker image rm "$TEMP_IMAGE" >/dev/null 2>&1 || true
```

검증 성공 조건:

1. 원본과 다른 Digest의 이미지를 생성함
2. 동일한 태그 Push가 실패함
3. 출력에서 `ImageTagAlreadyExistsException`을 확인함
4. 검증 전후 원격 Digest가 일치함
5. 검증용 로컬 이미지와 태그를 정리함

## 12. Lifecycle Policy

Lifecycle Policy는 다음 파일에서 관리합니다.

```text
docs/aws/ecr/lifecycle-policy.json
```

적용 기준:

```text
미태그 이미지: Push 후 7일이 지나면 삭제
manual- 이미지: 최근 10개만 유지
```

### 정책 적용

```bash
aws ecr put-lifecycle-policy \
  --repository-name "$ECR_REPOSITORY" \
  --lifecycle-policy-text file://docs/aws/ecr/lifecycle-policy.json \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --no-cli-pager
```

### 로컬·원격 정책 비교

```bash
TEMP_DIRECTORY="$(mktemp -d)"

jq -S . \
  docs/aws/ecr/lifecycle-policy.json \
  > "$TEMP_DIRECTORY/local-policy.json"

aws ecr get-lifecycle-policy \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'lifecyclePolicyText' \
  --output text \
  --no-cli-pager \
  | jq -S . \
  > "$TEMP_DIRECTORY/remote-policy.json"

diff -u \
  "$TEMP_DIRECTORY/local-policy.json" \
  "$TEMP_DIRECTORY/remote-policy.json"

rm -rf "$TEMP_DIRECTORY"
```

### Lifecycle Preview

적용된 원격 정책을 기준으로 Preview를 실행합니다.

```bash
aws ecr start-lifecycle-policy-preview \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --no-cli-pager

PREVIEW_STATUS=""

for ATTEMPT in $(seq 1 30)
do
  PREVIEW_STATUS="$(
    aws ecr get-lifecycle-policy-preview \
      --repository-name "$ECR_REPOSITORY" \
      --region "$AWS_REGION" \
      --profile "$AWS_PROFILE" \
      --query 'status' \
      --output text \
      --no-cli-pager
  )"

  case "$PREVIEW_STATUS" in
    COMPLETE)
      break
      ;;
    FAILED)
      echo "Lifecycle Policy Preview가 실패했습니다."
      exit 1
      ;;
  esac

  sleep 5
done

test "$PREVIEW_STATUS" = "COMPLETE"
```

현재 Preview 결과를 확인합니다.

```bash
aws ecr get-lifecycle-policy-preview \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query '{
    Status:status,
    ExpiringImageCount:summary.expiringImageTotalCount
  }' \
  --output json \
  --no-cli-pager
```

7일이 지난 미태그 이미지와 `manual-` 이미지 11개를 실제로 생성하는 검증은
대기 시간과 불필요한 테스트 이미지 누적을 고려하여
이번 Issue 범위에서는 제외합니다.

현재 Repository에서는 Lifecycle Preview를 통해
정책 적용 대상과 예상 삭제 결과를 검증합니다.

운영 이미지에는 `manual-` 접두사를 사용하지 않습니다.

ECR 이미지를 수동으로 삭제하기 전에는
ECS Task Definition이 해당 이미지 태그 또는 Digest를 참조하는지 확인합니다.

## 13. 최종 검증 결과

### 최종 검증 이미지

```text
Repository: otboo/backend
Tag: manual-6b5ce7d-amd64
Digest: sha256:6f1372fe6594b531f2c37565688bab2970ce060132a30bf476a780d950f80c1a
Scan completed at: 2026-08-03T10:15:33+09:00
```

### 최종 ECR 스캔 결과

```text
Status: COMPLETE
CRITICAL: 0
HIGH: 0
MEDIUM: 14
UNDEFINED: 2
```

이번 작업에서 확인된 수정 가능한 CRITICAL 및 HIGH 취약점은 모두 조치했습니다.

MEDIUM 및 UNDEFINED 항목은 베이스 이미지 갱신과
후속 운영 보안 점검 과정에서 지속적으로 확인합니다.

## 14. Issue #46 완료 기준

Issue #46에서는 다음 항목을 완료 기준으로 사용합니다.

```text
ECR Private Repository 구성
공통 태그 적용
IMMUTABLE 설정
AES256 암호화 확인
BASIC 및 SCAN_ON_PUSH 적용
베이스 이미지 Digest 고정
Git Commit 기반 이미지 태그 적용
빌드 전 작업 트리 검사
빌드 원본 Commit 고정 및 Push 단계 재사용
linux/amd64 이미지 빌드
ECR Basic Scan 호환 단일 이미지 Manifest 생성
비루트 사용자 실행
/app 디렉터리 및 JAR 권한 설정
불필요한 Wget 패키지 제거
수정 가능한 CRITICAL 및 HIGH 취약점 대응
ECR 이미지 Push
이미지 스캔 결과와 Digest 연결
스캔 결과 미충족 시 검증 실패
이미지 태그 불변성 확인
ECR 이미지 재Pull
Push·Pull Digest 일치 확인
Runtime 이미지 구성 검증
Lifecycle Policy 적용 및 원격 정책 비교
Lifecycle Policy Preview 완료 확인
AWS 인증정보 비노출 확인
```

Dockerfile, 애플리케이션 소스, Gradle 설정 및 의존성,
베이스 이미지 Digest, Runtime 패키지 기준 또는 이미지 빌드 방식이 변경된 경우에는
새 Git Commit SHA를 기준으로 이미지 빌드, ECR Push, 취약점 스캔,
Runtime 검증 및 Pull Digest 검증을 다시 수행합니다.

## 15. GitHub Actions ECR 자동 Push

Issue #124에서는 Issue #46에서 검증한 수동 ECR Push 절차를 기반으로
GitHub Actions에서 운영 Docker 이미지를 자동으로 빌드하고
Amazon ECR에 Push하는 Workflow를 구성했습니다.

이번 Issue의 범위는 ECR 이미지 Build 및 Push까지입니다.

ECS Task Definition Revision 등록과 ECS Service 자동 배포는
후속 배포 Issue에서 진행합니다.

### Workflow 파일

GitHub Actions Workflow는 다음 파일에서 관리합니다.

```text
.github/workflows/ecr-push.yml
```

기존 CI Workflow와 책임을 분리합니다.

```text
.github/workflows/ci.yml
→ 애플리케이션 Build 및 Test

.github/workflows/ecr-push.yml
→ 운영 Docker 이미지 Build 및 ECR Push
```

자동 Push 대상 Branch는 `develop`입니다.

수동 실행(`workflow_dispatch`)도 `develop` Branch에서만 수행합니다.
다른 Branch를 선택한 수동 실행은 AWS 인증 전에 Workflow에서 중단합니다.

```text
develop Push 또는 develop 기준 수동 실행
    ↓
GitHub Actions
    ↓
실행 Ref 검증
    ↓
GitHub OIDC Token 발급
    ↓
AWS STS를 통한 IAM Role Assume
    ↓
Amazon ECR 로그인
    ↓
동일 Git SHA 이미지 존재 여부 확인
    ├─ 존재함 → Digest / OCI Label / Platform 검증
    │             └─ 일치하면 성공 처리
    └─ 없음   → Docker Buildx
                  ↓
              linux/amd64 이미지 빌드
                  ↓
              Git Commit SHA 태그로 ECR Push
    ↓
최종 Image Digest 출력
```

수동 검증에 사용하는 사람용 AWS Profile이나
장기 AWS Access Key는 GitHub Actions에서 사용하지 않습니다.

### GitHub OIDC 인증

GitHub Actions에서 AWS 장기 인증정보를 저장하지 않도록
GitHub OIDC Provider와 전용 IAM Role을 구성했습니다.

```text
OIDC Provider: token.actions.githubusercontent.com
Audience: sts.amazonaws.com
IAM Role: otboo-github-actions-ecr-role
```

IAM Role Trust Policy는 GitHub에서 실제 발급된
immutable OIDC Subject 형식을 기준으로 구성합니다.

최종 운영 기준에서는 대상 Repository의 `develop` Branch만
Role을 Assume할 수 있도록 제한합니다.

```text
repo:{OWNER}@{OWNER_ID}/{REPOSITORY}@{REPOSITORY_ID}:ref:refs/heads/develop
```

Owner ID, Repository ID, AWS Account ID 및 IAM Role ARN 전체 값은
공개 문서에 기록하지 않습니다.

GitHub Repository에는 Role ARN을 다음 Repository Variable로 등록합니다.

```text
AWS_GITHUB_ACTIONS_ROLE_ARN
```

`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`와 같은
장기 AWS 인증정보는 GitHub Secrets에 등록하지 않습니다.

### IAM Role Trust Policy 기준

Issue #124 검증 중에는
`feature/124-github-actions-ecr` Branch도 일시적으로 허용하여
OIDC 인증과 ECR Push를 검증했습니다.

검증 완료 후에는 feature Branch Subject를 제거하고
`develop` Branch만 허용합니다.

최종 기준:

```text
Audience:
sts.amazonaws.com

Subject:
repo:{OWNER}@{OWNER_ID}/{REPOSITORY}@{REPOSITORY_ID}:ref:refs/heads/develop
```

Workflow 실행 Branch와 IAM Trust Policy의 허용 Branch를 동일하게 유지합니다.

`workflow_dispatch`는 GitHub UI에서 실행 Ref를 선택할 수 있으므로
Workflow에서도 AWS 인증 전에 `refs/heads/develop`인지 확인합니다.

### ECR 최소 권한

GitHub Actions IAM Role에는
`otboo/backend` Repository의 이미지 확인 및 Push에 필요한 권한만 부여합니다.

ECR 로그인에 필요한 권한:

```text
ecr:GetAuthorizationToken
```

`otboo/backend` Repository에 제한하는 이미지 조회·검증·Push 권한:

```text
ecr:BatchCheckLayerAvailability
ecr:BatchGetImage
ecr:DescribeImages
ecr:GetDownloadUrlForLayer
ecr:InitiateLayerUpload
ecr:UploadLayerPart
ecr:CompleteLayerUpload
ecr:PutImage
```

각 조회 권한은 다음 목적으로 사용합니다.

```text
ecr:DescribeImages
→ 동일 Git SHA 태그의 기존 이미지 존재 여부와 Digest 조회

ecr:BatchGetImage
→ Buildx 및 ECR 이미지 Manifest 조회

ecr:GetDownloadUrlForLayer
→ 기존 SHA 이미지 검증을 위한 Docker Pull
```

`ecr:GetAuthorizationToken`은 ECR 인증 특성상 전체 Resource를 대상으로 하며,
나머지 이미지 조회·다운로드·업로드 권한은
`otboo/backend` Repository ARN으로 제한합니다.

AWS 관리형 `AmazonEC2ContainerRegistryFullAccess` 정책은 사용하지 않습니다.

### Workflow 권한

GitHub Actions Workflow에는 OIDC Token 발급과
Repository Checkout에 필요한 권한만 선언합니다.

```yaml
permissions:
  contents: read
  id-token: write
```

`id-token: write`는 GitHub Actions가 OIDC Token을 발급받기 위해 사용합니다.

GitHub Repository Contents는 읽기 권한만 사용합니다.

### Docker Buildx 및 Platform

Workflow에서는 Docker Buildx를 사용해 운영 이미지를 빌드합니다.

운영 ECS Task의 CPU Architecture가 `X86_64`이므로
다음 Platform으로 고정합니다.

```text
linux/amd64
```

Dockerfile 내부 Builder Stage에서 Gradle `bootJar`를 생성하므로
ECR Workflow에서 별도의 Gradle 빌드 결과물을 준비하지 않습니다.

Workflow는 기존 운영 Dockerfile을 그대로 사용합니다.

```text
Dockerfile
```

ECR Basic Scan과 단일 이미지 Manifest 기준을 유지하기 위해
GitHub Actions Build에서도 Provenance Attestation을 비활성화합니다.

```yaml
provenance: false
```

SBOM과 Provenance 적용은 후속 공급망 보안 점검에서 별도로 검토합니다.

### 자동 이미지 태그

GitHub Actions에서 생성하는 운영 이미지에는
Workflow를 실행한 Git Commit의 전체 SHA를 태그로 사용합니다.

```text
{ECR Repository URI}:{Git Commit SHA 40자리}
```

예시:

```text
otboo/backend:0123456789abcdef0123456789abcdef01234567
```

Repository가 `IMMUTABLE`로 설정되어 있으므로
`latest` 태그는 사용하지 않습니다.

Workflow에서는 동일한 Commit SHA를 Dockerfile의 빌드 인자로 전달합니다.

```text
IMAGE_SOURCE_COMMIT=${{ github.sha }}
```

Dockerfile은 해당 값을 다음 OCI Label에 기록합니다.

```text
org.opencontainers.image.revision
```

따라서 다음 세 값이 동일한 Git Commit을 가리켜야 합니다.

```text
Git Commit SHA
=
ECR Image Tag
=
org.opencontainers.image.revision OCI Label
```

### IMMUTABLE 태그 재실행 처리

ECR Repository는 `IMMUTABLE`이므로
이미 존재하는 Git SHA 태그에 이미지를 다시 Push할 수 없습니다.

GitHub Actions의 Re-run 또는 동일 Commit에서의 수동 재실행이
단순히 `ImageTagAlreadyExistsException`으로 실패하지 않도록
Push 전에 동일 SHA 태그의 존재 여부를 확인합니다.

동일 SHA 태그가 없는 경우:

```text
Docker Buildx
→ linux/amd64 빌드
→ Git SHA 태그 ECR Push
```

동일 SHA 태그가 이미 있는 경우:

```text
ECR Image Digest 조회
→ 기존 이미지 Pull
→ Pull Digest와 ECR Digest 비교
→ Architecture가 amd64인지 확인
→ OCI revision Label이 Git SHA와 같은지 확인
→ 모두 일치하면 기존 immutable 이미지를 정상 결과로 사용
→ 하나라도 일치하지 않으면 Workflow 실패
```

기존 이미지를 검증하지 않은 채 성공 처리하거나
Repository를 `MUTABLE`로 변경하여 덮어쓰지 않습니다.

### GitHub Actions ECR Push Workflow

운영 이미지와 ECS 배포는 단순히 Docker Build가 성공한 Commit이 아니라
CI 검증을 통과한 최신 `develop` Commit만 대상으로 합니다.

현재 Workflow 흐름은 다음과 같습니다.

```text
develop Push
→ 현재 develop 최신 Git SHA 확인
→ CI Quality Gate 실행
→ CI 성공
→ 이미지 Build 직전 최신 Git SHA 재확인
→ 기존 immutable 이미지 확인 또는 Docker Build
→ ECR Push
→ ECS 배포 직전 최신 Git SHA 재확인
→ ECS Deploy
→ Service·Task·Target Health·Application Health 검증
```

`ci.yml`은 `workflow_call`을 제공하며,
`develop` Push에 대해서는 ECR/ECS Workflow가 CI를 선행 호출합니다.

따라서 `develop` Push 시 CI와 운영 배포 Workflow가 서로 독립적으로 실행되지 않으며,
테스트가 실패하면 ECR 이미지 Build·Push와 ECS 배포가 진행되지 않습니다.

```yaml
jobs:
  validate-source:
    # Workflow가 현재 develop 최신 SHA를 대상으로 하는지 검증

  quality-gate:
    needs: validate-source
    uses: ./.github/workflows/ci.yml

  build-and-push:
    needs: quality-gate
    # CI 성공 후에만 이미지 검증·Build·Push 수행

  deploy-to-ecs:
    needs: build-and-push
    # 최신 develop SHA를 다시 확인한 뒤 ECS 배포
```

Workflow 동시 실행 정책은 다음과 같습니다.

```yaml
concurrency:
  group: ecr-push-${{ github.ref }}
  cancel-in-progress: false
```

이미 진행 중인 운영 배포를 새 Push가 강제로 취소하지 않습니다.

또한 이전 Commit의 Workflow를 수동으로 재실행하거나,
Workflow가 실행되는 동안 더 최신 `develop` Commit이 추가되는 경우를 대비해
이미지 Build 직전과 ECS 배포 직전에 현재 `develop` HEAD를 다시 조회합니다.

```text
Workflow Git SHA == 현재 develop HEAD
→ 계속 진행

Workflow Git SHA != 현재 develop HEAD
→ stale 실행으로 판단
→ 이미지 Build 또는 ECS 배포 차단
```

이를 통해 이전 Commit의 Workflow가 늦게 실행되거나 재실행되더라도
최신 운영 버전을 오래된 이미지가 덮어쓰는 상황을 방지합니다.

동일 Git SHA 이미지가 ECR에 이미 존재하는 경우에는
새 이미지를 다시 Push하지 않고 기존 immutable 이미지를 Pull하여 다음 항목을 검증합니다.

```text
ECR Digest == Pull한 이미지 Digest
Platform == linux/amd64
OCI revision Label == Git SHA
```

검증에 성공한 경우 기존 이미지를 그대로 ECS 배포 대상으로 사용합니다.

ECR Repository의 immutable 정책은 유지하며,
동일 Git SHA 태그를 덮어쓰지 않습니다.

### ECR Push 검증

Workflow 성공 후 검증할 자동 Push 이미지를
Workflow를 실행한 Git Commit SHA 기준으로 설정합니다.

수동 검증에서 사용하는 `manual-{Short SHA}-amd64` 태그와 혼동하지 않도록
자동 Push 검증에서는 관련 변수를 다시 설정합니다.

```bash
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"
ECR_REPOSITORY="otboo/backend"

IMAGE_SOURCE_COMMIT="{Git Commit SHA 40자리}"
IMAGE_TAG="$IMAGE_SOURCE_COMMIT"

ECR_REPOSITORY_URI="$(
  aws ecr describe-repositories \
    --repository-names "$ECR_REPOSITORY" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'repositories[0].repositoryUri' \
    --output text \
    --no-cli-pager
)"

ECR_IMAGE="${ECR_REPOSITORY_URI}:${IMAGE_TAG}"

IMAGE_DIGEST="$(
  aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'imageDetails[0].imageDigest' \
    --output text \
    --no-cli-pager
)"
```

`IMAGE_SOURCE_COMMIT`에는 검증하려는 GitHub Actions 실행의
전체 Git Commit SHA 40자리를 입력합니다.

예시:

```text
IMAGE_SOURCE_COMMIT="0123456789abcdef0123456789abcdef01234567"
```

자동 Push 이미지 정보를 확인합니다.

```bash
aws ecr describe-images \
  --repository-name "$ECR_REPOSITORY" \
  --image-ids imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'imageDetails[0].{
    Tags:imageTags,
    Digest:imageDigest,
    PushedAt:imagePushedAt,
    Size:imageSizeInBytes
  }' \
  --output table \
  --no-cli-pager
```

검증 항목:

```text
Git Commit SHA 기반 Image Tag 존재
Image Digest 정상 조회
ECR Push 시각 정상 조회
```

Issue #124 검증에서는 Git Commit SHA 기반 이미지가 실제 ECR에 Push되었고
Image Digest가 정상적으로 조회되는 것을 확인했습니다.

### Pull 및 OCI Label 검증

운영 이미지는 `linux/amd64` 단일 Platform으로 생성합니다.

Apple Silicon Mac에서 Pull할 경우
로컬 Docker의 기본 Platform이 `linux/arm64/v8`이므로
Platform을 명시하지 않으면 다음 오류가 발생할 수 있습니다.

```text
no matching manifest for linux/arm64/v8
```

이 경우 운영 Platform을 명시해 Pull합니다.

```bash
docker pull \
  --platform linux/amd64 \
  "$ECR_IMAGE"
```

Pull된 이미지의 Digest를 확인합니다.

```bash
PULLED_DIGEST="$(
  docker image inspect "$ECR_IMAGE" \
    --format '{{range .RepoDigests}}{{println .}}{{end}}' \
  | grep "^${ECR_REPOSITORY_URI}@" \
  | head -n 1 \
  | cut -d '@' -f 2
)"

test "$IMAGE_DIGEST" = "$PULLED_DIGEST" || {
  echo "ECR Digest와 Pull 이미지 Digest가 일치하지 않습니다."
  exit 1
}
```

Pull된 이미지의 OS와 Architecture를 확인합니다.

```bash
test "$(docker image inspect "$ECR_IMAGE" --format '{{.Os}}')" = "linux"
test "$(docker image inspect "$ECR_IMAGE" --format '{{.Architecture}}')" = "amd64"
```

Pull된 이미지의 OCI revision Label을 확인합니다.

```bash
PULLED_LABEL_COMMIT="$(
  docker image inspect "$ECR_IMAGE" \
    --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
)"

test "$PULLED_LABEL_COMMIT" = "$IMAGE_SOURCE_COMMIT" || {
  echo "OCI revision Label과 자동 Push 원본 Commit이 일치하지 않습니다."
  exit 1
}
```

검증 성공 조건:

```text
Git Commit SHA
=
ECR Image Tag
=
OCI revision Label

ECR Image Digest
=
Pull 이미지 Digest

OS
=
linux

Architecture
=
amd64
```

Issue #124에서는 Git Commit SHA, ECR Image Tag 및 OCI revision Label이
동일한 Commit을 가리키는 것을 확인했습니다.

또한 ECR에서 조회한 Image Digest와
Pull된 이미지의 RepoDigest가 동일함을 확인합니다.

### 수동 실행 Branch 제한 검증

`workflow_dispatch`는 수동 실행 시 Ref를 선택할 수 있으므로
`develop` 이외의 Branch를 선택한 경우 AWS 인증 전에 실패하도록 구성합니다.

Workflow Guard:

```yaml
- name: Validate workflow ref
  if: github.event_name == 'workflow_dispatch' && github.ref != 'refs/heads/develop'
  run: |
    echo "Manual ECR push is allowed only from develop."
    exit 1
```

검증 기준:

```text
develop 수동 실행
→ AWS OIDC 인증 단계 진행

develop 이외 Branch 수동 실행
→ Validate workflow ref 단계에서 실패
→ AWS 인증 시도 없음
```

### 권한 오류 검증

Workflow 구성 과정에서 최소 권한이 실제로 적용되는지도 확인했습니다.

#### OIDC Trust Policy Subject 불일치

초기 Trust Policy에서는 Repository 이름 기반 Subject를 사용했습니다.

```text
repo:{OWNER}/{REPOSITORY}:ref:refs/heads/{BRANCH}
```

그러나 실제 GitHub OIDC Token은
Owner ID와 Repository ID가 포함된 immutable Subject 형식으로 발급되었습니다.

```text
repo:{OWNER}@{OWNER_ID}/{REPOSITORY}@{REPOSITORY_ID}:ref:refs/heads/{BRANCH}
```

이로 인해 다음 오류가 발생했습니다.

```text
Not authorized to perform sts:AssumeRoleWithWebIdentity
```

실제 OIDC Claim을 확인한 뒤
Trust Policy를 immutable Subject 형식으로 변경하여 해결했습니다.

OIDC 진단 과정에서는 Token 자체를 출력하지 않고,
`sub`, `aud`, Repository, Ref 등 필요한 Claim만 확인했습니다.

검증 완료 후 OIDC Claim 출력 Step은 Workflow에서 제거했습니다.

#### ECR Manifest 조회 권한 부족

초기 ECR Push 정책에는
일반적인 이미지 업로드 권한만 포함되어 있었습니다.

Docker Buildx Push 과정에서 Manifest 조회를 시도하면서
다음 오류가 발생했습니다.

```text
403 Forbidden
```

원인은 IAM Role에 `ecr:BatchGetImage` 권한이 없었던 것이었습니다.

다음 권한을 `otboo/backend` Repository에 한정해 추가한 뒤
정상 Push를 확인했습니다.

```text
ecr:BatchGetImage
```

#### IMMUTABLE 재실행 검증용 조회 권한

동일 Git SHA 태그의 재실행을 안전하게 처리하기 위해
기존 이미지 존재 여부와 Digest를 조회하고
이미지를 Pull하여 Label과 Platform을 검증합니다.

이에 따라 다음 조회 권한을
`otboo/backend` Repository에 한정해 추가합니다.

```text
ecr:DescribeImages
ecr:GetDownloadUrlForLayer
```

오류 해결 과정에서도
ECR 전체 Repository나 AWS 계정 전체에 대한
광범위한 이미지 Push 권한은 부여하지 않습니다.

### 권한 오류 재검증 기준

향후 IAM Policy를 변경하는 경우
정상 Push만 확인하지 않고 권한 범위도 함께 검증합니다.

확인 항목:

```text
GitHub OIDC 인증 성공
대상 Repository Push 성공
기존 SHA 이미지 조회 및 Pull 성공
허용되지 않은 Resource에 대한 권한 없음
장기 AWS Access Key 미사용
Trust Policy가 develop Branch로 제한됨
```

권한 오류가 발생한 경우
AWS 관리형 FullAccess 정책을 임시로 추가하기보다
실패한 API Action을 확인한 뒤 필요한 최소 권한만 추가합니다.

### Lifecycle Policy와 자동 이미지

현재 Lifecycle Policy는 미태그 이미지와 `manual-` 이미지의 정리 기준을 관리합니다.

GitHub Actions에서 생성되는 전체 Git SHA 태그 이미지는
`manual-` 접두사를 사용하지 않으므로 현재 `manual-` 보관 규칙의 대상이 아닙니다.

자동 Push 이미지의 장기 보관 개수 또는 기간 제한이 필요한 경우에는
후속 운영 점검에서 별도 Lifecycle 규칙을 검토합니다.

### 최종 검증 기준

Issue #124에서는 다음 항목을 완료 기준으로 검증합니다.

```text
GitHub OIDC Provider 구성
GitHub Actions용 IAM Role 구성
immutable OIDC Subject 기반 Trust Policy 제한
develop Branch 기준 최종 Trust Policy 구성
workflow_dispatch develop Branch Guard 적용
장기 AWS Access Key 미사용
otboo/backend Repository 기준 최소 ECR 권한 적용
GitHub Repository Variable을 통한 Role ARN 참조
Amazon ECR 로그인 성공
기존 Git SHA 이미지 존재 여부 조회
IMMUTABLE 태그 재실행 시 기존 이미지 검증
ECR Digest와 Pull Digest 일치 확인
linux/amd64 Platform 확인
Docker Buildx 구성
Provenance Attestation 비활성화
linux/amd64 운영 이미지 빌드 성공
Git Commit SHA 기반 Image Tag 생성
latest 태그 미사용
ECR Push 성공
Image Digest 정상 조회
Git Commit SHA와 OCI revision Label 일치
권한 부족 상황에서 Workflow 실패 확인
최종 Workflow 정상 실행 확인
```

자동 ECR Push까지 Issue #124에서 완료했으며,
ECS Task Definition Revision 생성과 Service 자동 배포는 Issue #131에서 구성합니다.

## 16. 후속 작업

* Issue #21: RDS PostgreSQL 및 S3 운영 환경 구성
* 별도 이슈: 운영 Redis 또는 ElastiCache 구성
* Issue #22: ECS Cluster, Task Definition, Service 및 ALB 구성
* Issue #124: GitHub Actions OIDC 기반 이미지 빌드 및 ECR Push 구성
* Issue #131: ECR Git SHA 이미지 기반 ECS Task Definition 갱신 및 Service 자동 배포
* 후속 보안 점검: MEDIUM 및 UNDEFINED 취약점 영향 분석
* 후속 운영 점검: 베이스 이미지 Digest 갱신
* 후속 공급망 보안 점검: Provenance 및 SBOM 적용

GitHub Actions에서는 사람용 IAM 사용자의 Access Key를 사용하지 않습니다.

GitHub Actions 기반 ECR 자동 Push는 Issue #124에서 구성했습니다.
Issue #131에서는 ECR에 Push된 Git SHA 이미지를 기준으로
ECS Task Definition Revision 등록과 ECS Service 자동 배포를 구성합니다.