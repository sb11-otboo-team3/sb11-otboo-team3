# Amazon ECR Private Repository 구성 및 이미지 검증

## 1. 개요

이 문서는 `otboo` Spring Boot 애플리케이션의 Docker 이미지를 저장하기 위한 Amazon ECR Private Repository 구성과 수동 이미지 검증 절차를 정리합니다.

로컬에서 `linux/amd64` 이미지를 빌드하고 Git Commit SHA 기반 태그를 적용한 뒤 ECR에 Push합니다. 이후 로컬 이미지를 제거하고 ECR에서 다시 Pull하여 Push한 이미지와 Pull한 이미지가 동일한지 검증합니다.

검증 범위에는 다음 항목이 포함됩니다.

* ECR Private Repository 설정
* 이미지 태그 불변성
* Registry 수준 Scan on Push
* 취약점 스캔 및 수정 가능한 취약점 대응
* Lifecycle Policy
* 이미지 Push 및 Pull
* 이미지 Digest 비교
* OS 및 Architecture 확인
* 비루트 사용자 실행
* Java Runtime 구성
* 애플리케이션 JAR 확인
* AWS 인증정보 노출 여부 확인

GitHub Actions 자동 ECR Push, OIDC IAM Role, ECS Cluster, Task Definition, Service 및 무중단 배포는 후속 이슈에서 진행합니다.

---

## 2. 기본 환경

```text
AWS Region: ap-northeast-2
AWS CLI Profile: otboo
ECR Repository: otboo/backend
Repository Type: Private
```

실제 AWS 계정 ID, Repository URI, ARN 및 인증정보는 Git 저장소와 공개 문서에 기록하지 않습니다.

---

## 3. ECR Repository 설정

Repository에는 다음 설정을 적용합니다.

```text
Repository name: otboo/backend
Image tag mutability: IMMUTABLE
Mutability exclusion: 없음
Encryption: AES256
Registry scan type: BASIC
Scan frequency: SCAN_ON_PUSH
```

### 공통 태그

```text
Project=otboo
Environment=shared
Owner=team3
ManagedBy=manual
Purpose=container-registry
```

### Repository 설정 확인

```bash
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"
ECR_REPOSITORY="otboo/backend"

aws ecr describe-repositories \
  --repository-names "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'repositories[0].{
    RepositoryName:repositoryName,
    Mutability:imageTagMutability,
    Exclusions:imageTagMutabilityExclusionFilters,
    Encryption:encryptionConfiguration.encryptionType
  }' \
  --output json \
  --no-cli-pager
```

기대 설정:

```text
RepositoryName: otboo/backend
Mutability: IMMUTABLE
Exclusions: 없음
Encryption: AES256
```

---

## 4. Registry 이미지 스캔 설정

ECR Private Registry의 스캔 유형은 `BASIC`을 사용합니다.

`otboo/backend` Repository는 `SCAN_ON_PUSH` 필터 대상으로 지정하여 새로운 이미지가 Push될 때 자동으로 취약점 스캔을 실행합니다.

### Registry 설정 확인

```bash
aws ecr get-registry-scanning-configuration \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --output json \
  --no-cli-pager
```

확인된 설정:

```text
Scan type: BASIC
Scan frequency: SCAN_ON_PUSH
Repository filter: otboo/backend
Filter type: WILDCARD
```

### Repository 적용 결과 확인

```bash
aws ecr batch-get-repository-scanning-configuration \
  --repository-names "$ECR_REPOSITORY" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --output json \
  --no-cli-pager
```

확인된 결과:

```text
Repository: otboo/backend
scanOnPush: true
scanFrequency: SCAN_ON_PUSH
Applied filter: otboo/backend
Failures: 없음
```

---

## 5. 이미지 태그 규칙

수동 검증 이미지에는 Git Commit Short SHA 7자리와 Architecture를 포함한 태그를 사용합니다.

```text
manual-{Git Commit Short SHA 7자리}-amd64
```

예시:

```text
manual-abcdef1-amd64
```

### 태그 운영 원칙

* `latest` 태그를 사용하지 않습니다.
* Dockerfile 또는 애플리케이션 코드 변경 후 먼저 Git Commit을 생성합니다.
* 이미지 태그의 SHA는 이미지에 포함된 Dockerfile 및 애플리케이션 코드의 Commit SHA와 일치해야 합니다.
* 하나의 SHA와 Architecture 조합은 하나의 변경 불가능한 이미지를 의미합니다.
* 문제 분석 과정에서 사용하는 `-r2`, `-r3` 등의 접미사 태그는 최종 완료 이미지로 사용하지 않습니다.
* 최종 검증 이미지는 정확히 `manual-{SHA}-amd64` 형식을 사용합니다.
* 문서만 변경한 후속 Commit 때문에 브랜치 HEAD가 달라진 경우 이미지를 다시 빌드하지 않습니다.
* 문서 Commit 이후에도 이미지에 영향을 주는 파일이 변경되지 않았는지 확인합니다.

문서 Commit 이후 이미지 관련 변경 확인:

```bash
git diff <IMAGE_SOURCE_SHA>..HEAD \
  -- Dockerfile \
     build.gradle \
     settings.gradle \
     gradle \
     src
```

출력이 없다면 이미지 생성 이후 문서만 변경된 것이므로 재빌드하지 않습니다.

---

## 6. ECR 환경변수 설정

```bash
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"
ECR_REPOSITORY="otboo/backend"

GIT_SHA="$(git rev-parse --short=7 HEAD)"

ECR_REPOSITORY_URI="$(
  aws ecr describe-repositories \
    --repository-names "$ECR_REPOSITORY" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'repositories[0].repositoryUri' \
    --output text
)"

ECR_REGISTRY="${ECR_REPOSITORY_URI%%/*}"
IMAGE_TAG="manual-${GIT_SHA}-amd64"
LOCAL_IMAGE="otboo:${IMAGE_TAG}"
ECR_IMAGE="${ECR_REPOSITORY_URI}:${IMAGE_TAG}"
```

실제 Repository URI는 로컬 환경변수로만 사용하며 문서, Issue 및 PR에 직접 기록하지 않습니다.

---

## 7. Docker 이미지 빌드

ECS 환경을 고려하여 수동 검증 이미지는 `linux/amd64` 대상으로 빌드합니다.

Dockerfile의 `FROM` 구문에는 특정 플랫폼을 고정하지 않고 빌드 명령에서 플랫폼을 지정합니다.

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

### 로컬 이미지 설정 확인

```bash
docker image inspect "$LOCAL_IMAGE" \
  --format 'os={{.Os}} architecture={{.Architecture}} user={{.Config.User}}'
```

기대 결과:

```text
os=linux architecture=amd64 user=otboo
```

---

## 8. Runtime 이미지 보안 업데이트

Runtime 이미지는 `eclipse-temurin:17-jre-jammy`를 사용합니다.

전체 OS 패키지를 일괄 변경하는 `apt-get upgrade`는 수행하지 않습니다. ECR 스캔에서 확인된 수정 가능한 취약 패키지만 `--only-upgrade` 방식으로 제한적으로 업데이트합니다.

### glibc 관련 패키지

대응 대상:

```text
CVE-2026-5450
```

패키지:

```text
libc6
libc-bin
locales
```

기존 버전:

```text
2.35-0ubuntu3.13
```

적용 버전:

```text
2.35-0ubuntu3.14
```

### Wget

대응 대상:

```text
CVE-2026-58469
```

패키지:

```text
wget
```

기존 버전:

```text
1.21.2-2ubuntu1.1
```

적용 버전:

```text
1.21.2-2ubuntu1.4
```

### Kerberos 관련 패키지

대응 대상:

```text
CVE-2026-40355
CVE-2026-40356
```

패키지:

```text
libkrb5-3
libkrb5support0
libgssapi-krb5-2
libk5crypto3
```

기존 버전:

```text
1.19.2-2ubuntu0.7
```

적용 버전:

```text
1.19.2-2ubuntu0.8
```

Dockerfile에서는 Wget과 Kerberos 패키지가 최소 보안 수정 버전에 도달하지 못하면 이미지 빌드가 실패하도록 버전 검증을 수행합니다.

---

## 9. 로컬 이미지 패키지 확인

```bash
docker run --rm \
  --platform linux/amd64 \
  --entrypoint dpkg-query \
  "$LOCAL_IMAGE" \
  -W \
  -f='${binary:Package}=${Version}\n' \
  libc6 \
  libc-bin \
  locales \
  wget \
  libkrb5-3 \
  libkrb5support0 \
  libgssapi-krb5-2 \
  libk5crypto3
```

최종 검증 버전:

```text
libc-bin=2.35-0ubuntu3.14
libc6:amd64=2.35-0ubuntu3.14
locales=2.35-0ubuntu3.14
wget=1.21.2-2ubuntu1.4
libkrb5-3:amd64=1.19.2-2ubuntu0.8
libkrb5support0:amd64=1.19.2-2ubuntu0.8
libgssapi-krb5-2:amd64=1.19.2-2ubuntu0.8
libk5crypto3:amd64=1.19.2-2ubuntu0.8
```

---

## 10. Java Runtime 및 실행 파일 검증

```bash
docker run --rm \
  --platform linux/amd64 \
  --entrypoint sh \
  "$LOCAL_IMAGE" \
  -c '
    id
    java -version

    if command -v javac >/dev/null 2>&1; then
      echo "javac 포함"
      exit 1
    else
      echo "javac 없음"
    fi

    if test -f /app/app.jar; then
      echo "/app/app.jar 존재"
    else
      echo "/app/app.jar 없음"
      exit 1
    fi
  '
```

검증 결과:

```text
Java 17 실행 확인
javac 미포함
/app/app.jar 존재
실행 사용자 otboo
비루트 사용자 실행
```

---

## 11. ECR 로그인

ECR 로그인에는 Access Key나 비밀번호를 명령어에 직접 작성하지 않고 `get-login-password`와 `--password-stdin`을 사용합니다.

```bash
aws ecr get-login-password \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  | docker login \
      --username AWS \
      --password-stdin "$ECR_REGISTRY"
```

---

## 12. 이미지 Push

```bash
docker tag "$LOCAL_IMAGE" "$ECR_IMAGE"
docker push "$ECR_IMAGE"
```

Push 후 다음 항목을 확인합니다.

* 이미지 태그
* 이미지 Digest
* 이미지 Push 시각
* 이미지 크기
* 스캔 실행 여부
* 스캔 완료 상태

---

## 13. 이미지 스캔 결과

### 스캔 상태 확인

```bash
aws ecr wait image-scan-complete \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

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

최종 스캔 결과:

```text
Status: COMPLETE
CRITICAL: 0
HIGH: 0
MEDIUM: 14
UNDEFINED: 2
```

### 대응 CVE 제거 확인

최종 이미지에서 다음 CVE가 더 이상 조회되지 않는 것을 확인했습니다.

```text
CVE-2026-5450
CVE-2026-58469
CVE-2026-40355
CVE-2026-40356
```

검증 명령:

```bash
aws ecr describe-image-scan-findings \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'imageScanFindings.findings[?
    name==`CVE-2026-5450` ||
    name==`CVE-2026-58469` ||
    name==`CVE-2026-40355` ||
    name==`CVE-2026-40356`
  ].[severity,name,attributes]' \
  --output json \
  --no-cli-pager
```

정상 결과:

```json
[]
```

MEDIUM 및 UNDEFINED 결과는 향후 베이스 이미지 갱신과 운영 보안 점검 과정에서 지속적으로 확인합니다.

새로운 수정 버전이 제공되거나 베이스 이미지가 갱신되는 경우 새로운 Git Commit SHA를 기준으로 이미지를 다시 빌드하고 스캔합니다.

---

## 14. 이미지 태그 불변성 검증

Repository는 예외 필터가 없는 `IMMUTABLE`로 설정합니다.

동일한 이미지를 같은 태그로 다시 Push하는 것은 불변성 검증으로 사용하지 않습니다. 동일한 Manifest와 Digest는 ECR에서 다시 받아들여질 수 있기 때문입니다.

불변성 검증에서는 원본과 Digest가 다른 임시 이미지를 생성한 뒤 동일한 최종 태그로 Push합니다.

검증 결과:

```text
다른 Digest의 동일 태그 Push 차단
ImageTagAlreadyExistsException 확인
불변성 검증 전후 원격 Digest 일치
원격 이미지 변경 없음
로컬 ECR 태그 원본 복구
임시 검증 이미지 삭제
```

따라서 기존 태그를 다른 이미지로 덮어쓸 수 없음을 확인했습니다.

---

## 15. 이미지 Pull 및 Digest 검증

먼저 ECR에 저장된 Digest를 조회합니다.

```bash
ECR_DIGEST="$(
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

로컬의 ECR 태그 이미지를 제거합니다.

```bash
docker image rm "$ECR_IMAGE"
```

ECR에서 이미지를 다시 Pull합니다.

```bash
docker pull "$ECR_IMAGE"
```

Pull 이미지의 RepoDigest를 조회합니다.

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
  echo "Push 이미지와 Pull 이미지 Digest 불일치"
  exit 1
fi
```

검증 결과:

```text
ECR Image Digest와 Pull 이미지 RepoDigest 일치
Push한 이미지와 Pull한 이미지 동일
```

---

## 16. 최종 Pull 이미지 검증

Pull한 최종 이미지에서 다음 항목을 다시 확인했습니다.

```text
OS: linux
Architecture: amd64
실행 사용자: otboo
Java: 17
javac: 미포함
애플리케이션 JAR: /app/app.jar
```

확인 명령:

```bash
docker image inspect "$ECR_IMAGE" \
  --format 'os={{.Os}} architecture={{.Architecture}} user={{.Config.User}}'
```

```bash
docker run --rm \
  --platform linux/amd64 \
  --entrypoint sh \
  "$ECR_IMAGE" \
  -c '
    id
    java -version

    if command -v javac >/dev/null 2>&1; then
      echo "javac 포함"
      exit 1
    else
      echo "javac 없음"
    fi

    test -f /app/app.jar
  '
```

Pull 이미지에서도 다음 보안 패키지 수정 버전을 확인했습니다.

```text
libc6: 2.35-0ubuntu3.14
libc-bin: 2.35-0ubuntu3.14
locales: 2.35-0ubuntu3.14
wget: 1.21.2-2ubuntu1.4
Kerberos 관련 패키지: 1.19.2-2ubuntu0.8
```

---

## 17. Lifecycle Policy

Lifecycle Policy 파일은 다음 경로에서 관리합니다.

```text
docs/aws/ecr/lifecycle-policy.json
```

정책 기준:

```text
미태그 이미지: Push 후 7일이 지나면 삭제
manual- 이미지: 최근 10개만 유지
```

### Lifecycle Policy Preview

정책 적용 전에 Preview를 실행하여 삭제 대상 이미지를 확인합니다.

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
    ExpiringImageTotalCount:summary.expiringImageTotalCount,
    Results:previewResults[].{
      Tags:imageTags,
      Priority:appliedRulePriority,
      Action:action.type
    }
  }' \
  --output json \
  --no-cli-pager
```

최종 Preview 결과:

```text
Status: COMPLETE
Expiring image total count: 0
예상하지 않은 삭제 대상: 없음
```

### 운영 이미지 보호 원칙

* Lifecycle Policy는 미태그 이미지와 `manual-` 태그 이미지만 대상으로 합니다.
* 운영 배포 이미지는 `manual-` 접두사를 사용하지 않습니다.
* 하나의 이미지에 수동 검증 태그와 운영 배포 태그를 함께 적용하지 않습니다.
* ECR 이미지를 수동 삭제하기 전에 ECS Task Definition이 해당 이미지 태그 또는 Digest를 참조하는지 확인합니다.
* 정책 변경 전 Lifecycle Policy Preview 결과를 확인합니다.

---

## 18. 보안 규칙

다음 정보는 Git 저장소, Issue, PR, README 및 스크린샷에 포함하지 않습니다.

```text
AWS Access Key
AWS Secret Access Key
AWS Session Token
ECR 인증 토큰
실제 AWS 계정 ID
실제 ECR Repository URI
실제 Repository ARN
운영 환경변수
실제 .env 파일
```

추가 보안 기준:

* Root 계정으로 ECR 작업을 수행하지 않습니다.
* AWS CLI Profile `otboo`를 사용합니다.
* ECR 인증에는 `docker login --password-stdin`을 사용합니다.
* Dockerfile에 AWS 인증정보를 작성하지 않습니다.
* Docker 이미지 환경변수와 History에 AWS 인증정보가 포함되지 않았는지 확인합니다.
* 취약점 원본 스캔 JSON은 Git 저장소에 커밋하지 않습니다.
* 실제 AWS 정보가 포함된 터미널 로그와 스크린샷을 공개 문서에 첨부하지 않습니다.

---

## 19. 최종 검증 결과

```text
ECR Private Repository 생성: 완료
Repository 공통 태그 적용: 완료
Image Tag Mutability IMMUTABLE: 완료
Mutability 예외 없음: 확인
AES256 암호화: 확인
Registry Scan Type BASIC: 확인
SCAN_ON_PUSH 적용: 확인
Lifecycle Policy 적용: 완료
Lifecycle Policy Preview: COMPLETE
예상 삭제 대상: 0
Git SHA 기반 이미지 태그: 적용
linux/amd64 이미지 빌드: 완료
ECR Push: 완료
이미지 스캔: COMPLETE
CRITICAL 취약점: 0
HIGH 취약점: 0
수정 가능한 CRITICAL 대응: 완료
수정 가능한 HIGH 대응: 완료
다른 Digest의 동일 태그 Push 차단: 확인
불변성 검증 전후 원격 Digest 일치: 확인
ECR 이미지 재Pull: 완료
Push 및 Pull Digest 일치: 확인
비루트 사용자 otboo 실행: 확인
Java 17: 확인
javac 미포함: 확인
/app/app.jar 존재: 확인
보안 패키지 수정 버전: 확인
AWS 인증정보 미포함: 확인
```

---

## 20. 후속 작업

다음 작업은 별도 이슈에서 진행합니다.

* GitHub Actions OIDC IAM Role
* GitHub Actions 자동 ECR Push
* 이미지 자동 태그 생성
* ECS Cluster
* ECS Task Definition
* ECS Task Execution Role
* ECS Task Role
* ECS Service
* Application Load Balancer
* Target Group
* ECS 자동 배포
* 무중단 배포
* 운영 이미지 보안 점검 자동화
