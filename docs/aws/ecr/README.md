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

GitHub Actions 자동 Push와 ECS 배포는 후속 이슈에서 진행합니다.

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

SBOM과 Provenance는 후속 GitHub Actions CD에서
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

## 15. 후속 작업

* Issue #21: RDS PostgreSQL 및 S3 운영 환경 구성
* 별도 이슈: 운영 Redis 또는 ElastiCache 구성
* Issue #22: ECS Cluster, Task Definition, Service 및 ALB 구성
* 후속 CD 이슈: GitHub Actions OIDC, ECR Push 및 ECS 자동 배포
* 후속 보안 점검: MEDIUM 및 UNDEFINED 취약점 영향 분석
* 후속 운영 점검: 베이스 이미지 Digest 갱신
* 후속 공급망 보안 점검: Provenance 및 SBOM 적용

GitHub Actions에서는 사람용 IAM 사용자의 Access Key를 사용하지 않습니다.

자동 배포는 OIDC 기반 IAM Role을 구성한 뒤
GitHub Actions에서 ECR Push와 ECS 배포를 수행하도록 확장합니다.