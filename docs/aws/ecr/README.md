# Amazon ECR 운영 및 이미지 검증 가이드

## 1. 개요

이 문서는 `옷장을 부탁해` 프로젝트에서 AWS ECS 배포에 사용할 Docker 이미지를 저장하기 위해 구성한 Amazon ECR Private Repository의 설정과 사용 방법을 설명합니다.

현재 단계에서는 로컬에서 Docker 이미지를 수동으로 빌드하여 ECR에 Push하고, 다시 Pull하여 이미지의 Digest와 실행 환경을 검증합니다.

GitHub Actions를 통한 자동 이미지 Push와 ECS 자동 배포는 후속 CD 이슈에서 OIDC 기반으로 구성합니다.

---

## 2. 기본 설정

| 항목                   | 설정값              |
| -------------------- | ---------------- |
| AWS Region           | `ap-northeast-2` |
| Repository 유형        | Private          |
| Repository 이름        | `otboo/backend`  |
| Image Tag Mutability | `IMMUTABLE`      |
| 암호화                  | `AES256`         |
| 이미지 Architecture     | `linux/amd64`    |
| AWS CLI Profile      | `otboo`          |

실제 AWS 계정 ID, Repository URI, Repository ARN 및 인증정보는 공개 문서에 작성하지 않습니다.

---

## 3. Repository 공통 태그

ECR Repository에는 다음 공통 태그를 적용합니다.

```text
Project=otboo
Environment=shared
Owner=team3
ManagedBy=manual
Purpose=container-registry
```

태그는 AWS 리소스의 소유 관계, 환경 및 용도를 식별하기 위해 사용합니다.

---

## 4. AWS 인증 원칙

ECR 작업은 Root 계정이 아닌 IAM 사용자를 통해 수행합니다.

로컬 AWS CLI에서는 다음 Profile을 사용합니다.

```text
otboo
```

현재 인증 주체 확인:

```bash
aws sts get-caller-identity \
  --profile otboo \
  --no-cli-pager
```

ECR 로그인은 Access Key나 비밀번호를 명령어에 직접 작성하지 않고, 임시 인증 토큰을 표준 입력으로 전달합니다.

```bash
aws ecr get-login-password \
  --region ap-northeast-2 \
  --profile otboo \
  | docker login \
      --username AWS \
      --password-stdin <ECR_REGISTRY>
```

다음 정보는 Issue, PR, 문서 또는 스크린샷에 노출하지 않습니다.

* AWS Access Key
* AWS Secret Access Key
* AWS Session Token
* ECR 인증 토큰
* 실제 AWS 계정 ID
* 전체 ECR Repository URI
* Repository ARN

---

## 5. 이미지 태그 규칙

수동 검증 이미지는 Git Commit Short SHA 7자리와 Architecture를 사용하여 태그를 생성합니다.

```text
manual-{Git Commit Short SHA}-amd64
```

예시:

```text
manual-a1b2c3d-amd64
```

### 태그 원칙

* `latest` 태그를 사용하지 않습니다.
* Dockerfile 또는 애플리케이션 코드가 변경되면 먼저 Git Commit을 생성합니다.
* 이미지 태그의 SHA는 이미지에 포함된 코드의 실제 Git Commit SHA와 일치해야 합니다.
* 최종 이미지 빌드 전 `git status`가 깨끗해야 합니다.
* 동일 태그는 다른 이미지로 덮어쓰지 않습니다.
* 문제 해결 과정에서 사용하는 `-r2`, `-r3` 등의 태그는 임시 검증용으로만 사용합니다.
* 최종 검증 이미지는 `manual-{SHA}-amd64` 형식을 사용합니다.

---

## 6. Docker 이미지 빌드

현재 Git Commit SHA를 확인합니다.

```bash
GIT_SHA="$(git rev-parse --short=7 HEAD)"
IMAGE_TAG="manual-${GIT_SHA}-amd64"
LOCAL_IMAGE="otboo:${IMAGE_TAG}"
```

작업 트리가 깨끗한지 확인합니다.

```bash
git status --short
```

커밋되지 않은 변경사항이 없어야 최종 이미지를 빌드합니다.

ECS 환경과 동일한 `linux/amd64` 플랫폼으로 이미지를 빌드합니다.

```bash
docker buildx build \
  --pull \
  --no-cache \
  --platform linux/amd64 \
  --provenance=false \
  --sbom=false \
  --tag "$LOCAL_IMAGE" \
  --load \
  .
```

Dockerfile의 `FROM` 구문에는 플랫폼을 고정하지 않습니다. 플랫폼은 빌드 명령에서 지정합니다.

---

## 7. 로컬 이미지 검증

### OS, Architecture 및 실행 사용자 확인

```bash
docker image inspect "$LOCAL_IMAGE" \
  --format 'os={{.Os}} architecture={{.Architecture}} user={{.Config.User}}'
```

기대값:

```text
os=linux architecture=amd64 user=otboo
```

### Java 17 확인

```bash
docker run --rm \
  --entrypoint java \
  "$LOCAL_IMAGE" \
  -version
```

### javac 미포함 확인

운영 이미지는 JDK가 아닌 JRE 기반으로 실행합니다.

```bash
docker run --rm \
  --entrypoint sh \
  "$LOCAL_IMAGE" \
  -c 'if command -v javac >/dev/null 2>&1; then
        echo "javac이 포함되어 있습니다."
        exit 1
      else
        echo "javac 없음"
      fi'
```

### 실행 사용자와 JAR 확인

```bash
docker run --rm \
  --entrypoint sh \
  "$LOCAL_IMAGE" \
  -c '
    id
    test -f /app/app.jar \
      && echo "/app/app.jar 존재" \
      || exit 1
  '
```

이미지는 Root가 아닌 `otboo` 사용자로 실행되어야 하며, `/app/app.jar`가 존재해야 합니다.

---

## 8. glibc 보안 취약점 대응

ECR 이미지 스캔 과정에서 `CVE-2026-5450`이 발견되었습니다.

취약점은 Temurin Jammy Runtime 이미지에 포함된 다음 `glibc` 계열 패키지에서 확인되었습니다.

```text
libc6
libc-bin
locales
```

기존 이미지에는 `2.35-0ubuntu3.13` 버전이 포함되어 있었습니다.

Dockerfile의 Runtime 단계에서 해당 패키지만 제한적으로 업데이트하여 수정 버전인 `2.35-0ubuntu3.14`를 적용했습니다.

전체 OS 패키지를 대상으로 하는 `apt-get upgrade`는 사용하지 않습니다.

패키지 버전 확인:

```bash
docker run --rm \
  --entrypoint dpkg-query \
  "$LOCAL_IMAGE" \
  -W \
  -f='${binary:Package}=${Version}\n' \
  libc6 \
  libc-bin \
  locales
```

기대값:

```text
libc-bin=2.35-0ubuntu3.14
libc6:amd64=2.35-0ubuntu3.14
locales=2.35-0ubuntu3.14
```

취약 버전 잔존 여부 확인:

```bash
docker run --rm \
  --entrypoint dpkg-query \
  "$LOCAL_IMAGE" \
  -W \
  -f='${binary:Package}\t${Version}\t${source:Package}\t${source:Version}\n' \
  | grep '2.35-0ubuntu3.13'
```

아무 결과도 출력되지 않아야 합니다.

---

## 9. ECR 이미지 Push

Repository URI는 AWS CLI를 통해 조회하여 환경변수에 저장합니다.

```bash
ECR_REPOSITORY="otboo/backend"
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"

ECR_REPOSITORY_URI="$(
  aws ecr describe-repositories \
    --repository-names "$ECR_REPOSITORY" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'repositories[0].repositoryUri' \
    --output text
)"

ECR_REGISTRY="${ECR_REPOSITORY_URI%%/*}"
ECR_IMAGE="${ECR_REPOSITORY_URI}:${IMAGE_TAG}"
```

ECR 로그인:

```bash
aws ecr get-login-password \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  | docker login \
      --username AWS \
      --password-stdin "$ECR_REGISTRY"
```

ECR 태그 적용 및 Push:

```bash
docker tag "$LOCAL_IMAGE" "$ECR_IMAGE"
docker push "$ECR_IMAGE"
```

Push 결과에서 이미지 Digest를 확인합니다.

---

## 10. Image Tag Mutability 검증

Repository는 `IMMUTABLE`로 설정합니다.

한 번 Push된 동일 태그를 다시 Push하면 차단되어야 합니다.

```bash
docker push "$ECR_IMAGE"
```

정상적인 검증 결과는 다음과 같은 오류입니다.

```text
ImageTagAlreadyExistsException
tag is immutable
```

이 오류는 배포 실패가 아니라 동일 태그 덮어쓰기가 차단됐다는 검증 결과입니다.

---

## 11. 이미지 스캔

Push된 이미지의 스캔 완료를 기다립니다.

```bash
aws ecr wait image-scan-complete \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

전체 결과 확인:

```bash
aws ecr describe-image-scan-findings \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query '{
    Status:imageScanStatus.status,
    Findings:imageScanFindings.findingSeverityCounts
  }' \
  --output json \
  --no-cli-pager
```

최종 검증 이미지에서 다음 조건을 충족해야 합니다.

* 스캔 상태가 `COMPLETE`
* 수정 가능한 `CRITICAL` 취약점이 없음
* `CVE-2026-5450`이 검색되지 않음

특정 CVE 확인:

```bash
aws ecr describe-image-scan-findings \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'imageScanFindings.findings[?name==`CVE-2026-5450`]' \
  --output json \
  --no-cli-pager
```

기대 결과:

```json
[]
```

### 취약점 대응 기준

* `CRITICAL` 취약점은 ECS 배포 전에 원인과 수정 가능 여부를 확인합니다.
* 수정 가능한 `CRITICAL` 취약점은 최종 운영 이미지에 남겨두지 않습니다.
* `HIGH` 취약점은 패키지 사용 여부와 실제 서비스 영향 범위를 확인합니다.
* 즉시 수정하기 어려운 취약점은 사유와 영향 범위를 문서화하고 후속 이슈 등록 여부를 결정합니다.

---

## 12. 이미지 Pull 및 Digest 검증

ECR에서 조회한 Digest를 저장합니다.

```bash
ECR_DIGEST="$(
  aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'imageDetails[0].imageDigest' \
    --output text
)"
```

로컬의 ECR 태그 이미지를 삭제합니다.

```bash
docker image rm "$ECR_IMAGE"
```

ECR에서 다시 Pull합니다.

```bash
docker pull "$ECR_IMAGE"
```

Pull 이미지의 RepoDigest를 확인합니다.

```bash
PULLED_DIGEST="$(
  docker image inspect "$ECR_IMAGE" \
    --format '{{range .RepoDigests}}{{println .}}{{end}}' \
  | awk -F@ -v repository="$ECR_REPOSITORY_URI" \
      '$1 == repository {print $2; exit}'
)"
```

Digest를 비교합니다.

```bash
if [ "$ECR_DIGEST" = "$PULLED_DIGEST" ]; then
  echo "Push 이미지와 Pull 이미지 Digest 일치"
else
  echo "Digest 불일치"
  exit 1
fi
```

Push 이미지와 Pull 이미지의 Digest가 일치해야 동일한 이미지가 저장되고 내려받아졌다고 판단합니다.

---

## 13. Lifecycle Policy

적용된 Lifecycle Policy는 다음 파일로 관리합니다.

```text
docs/aws/ecr/lifecycle-policy.json
```

### 적용 규칙

1. 미태그 이미지

    * Push 후 7일이 지나면 만료
2. `manual-` 접두사 이미지

    * 최근 10개만 유지
    * 오래된 이미지부터 만료

현재 적용된 정책은 `tagPrefixList`를 사용하여 `manual-`로 시작하는 태그를 대상으로 합니다.

Lifecycle Policy 확인:

```bash
aws ecr get-lifecycle-policy \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'lifecyclePolicyText' \
  --output text \
  --no-cli-pager \
  | python3 -m json.tool
```

### Preview

정책 적용 또는 변경 전에는 반드시 Preview를 확인합니다.

```bash
aws ecr start-lifecycle-policy-preview \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --no-cli-pager
```

```bash
aws ecr wait lifecycle-policy-preview-complete \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

```bash
aws ecr get-lifecycle-policy-preview \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query '{
    Status:status,
    ExpiringImageTotalCount:summary.expiringImageTotalCount
  }' \
  --output json \
  --no-cli-pager
```

### 운영 이미지 보호 원칙

* Lifecycle Policy는 미태그 이미지와 `manual-` 접두사 이미지만 대상으로 합니다.
* 운영 배포 이미지는 `manual-` 태그를 사용하지 않습니다.
* 하나의 이미지에 `manual-` 태그와 운영 태그를 함께 적용하지 않습니다.
* 이미지 삭제 전 ECS Task Definition에서 해당 태그 또는 Digest를 참조하는지 확인합니다.
* Lifecycle Policy Preview에서 예상하지 않은 운영 이미지가 삭제 대상으로 나오면 정책을 적용하거나 변경하지 않습니다.

---

## 14. 보안 점검

다음 정보가 Git 저장소에 포함되지 않았는지 확인합니다.

* AWS Access Key
* AWS Secret Access Key
* AWS Session Token
* 실제 `.env`
* 운영 환경변수 파일
* 실제 AWS 계정 ID
* ECR 인증 토큰
* 전체 Repository URI 및 ARN

Dockerfile과 Docker 이미지에도 AWS 인증정보를 포함하지 않습니다.

Docker Image History 확인:

```bash
docker history \
  --no-trunc \
  "$ECR_IMAGE"
```

Docker Image 환경변수 확인:

```bash
docker image inspect "$ECR_IMAGE" \
  --format '{{json .Config.Env}}'
```

GitHub Actions에서 ECR 자동 Push를 구성할 때는 장기 Access Key를 발급하지 않고 OIDC 기반 IAM Role을 사용합니다.

---

## 15. 후속 작업

이 이슈에서는 다음 항목을 진행하지 않습니다.

* GitHub Actions 자동 ECR Push
* GitHub Actions OIDC IAM Role
* ECS Cluster
* ECS Task Definition
* ECS Task Execution Role
* ECS Task Role
* ECS Service
* Application Load Balancer
* Target Group
* ECS 무중단 배포

GitHub Actions 자동 Push는 후속 CD 이슈에서 진행합니다.

ECS Task Definition과 Service 구성은 Issue #22에서 진행합니다.
