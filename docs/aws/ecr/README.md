# Amazon ECR 구성 및 이미지 검증

## 1. 작업 범위

Issue #46에서는 ECS에서 실행할 Docker 이미지를 저장하기 위한
Amazon ECR Private Repository를 구성하고 다음 항목을 검증합니다.

* ECR Private Repository 설정
* Repository 공통 태그 적용
* Git Commit 기반 이미지 태그
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

새 이미지 Push 시 자동으로 취약점 스캔이 실행되도록
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

계정 ID, Repository ARN 및 전체 Repository URI는
공개 문서에 기록하지 않습니다.

## 4. Docker 이미지 기준

### 베이스 이미지

Builder와 Runtime Stage에는 Eclipse Temurin Java 17 Jammy 이미지를 사용합니다.

베이스 이미지가 동일한 태그에서 임의로 변경되는 것을 줄이기 위해
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

빌드 전에 Docker 이미지에 영향을 주는 파일에
커밋되지 않은 변경사항이 없는지 확인합니다.

```bash
IMAGE_CONTEXT_CHANGES="$(
  git status --porcelain -- \
    Dockerfile \
    gradlew \
    gradle \
    build.gradle \
    settings.gradle \
    src
)"

if [ -n "$IMAGE_CONTEXT_CHANGES" ]; then
  echo "이미지 빌드에 영향을 주는 커밋되지 않은 변경사항이 있습니다."
  printf '%s\n' "$IMAGE_CONTEXT_CHANGES"
  exit 1
fi
```

현재 Git Commit으로 이미지 태그와 OCI Label 값을 생성합니다.

```bash
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"
ECR_REPOSITORY="otboo/backend"

IMAGE_SOURCE_COMMIT="$(git rev-parse HEAD)"
IMAGE_SOURCE_SHA="$(git rev-parse --short=7 "$IMAGE_SOURCE_COMMIT")"

IMAGE_TAG="manual-${IMAGE_SOURCE_SHA}-amd64"
LOCAL_IMAGE="otboo:${IMAGE_TAG}"
```

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
이미지가 OCI Image Index 형식으로 Push될 수 있으며,
ECR Basic Scan에서 해당 형식을 스캔하지 못할 수 있습니다.

SBOM과 Provenance는 후속 GitHub Actions CD에서
ECR 스캔 방식과 공급망 메타데이터 관리 방식을 함께 검토한 뒤 적용합니다.

## 7. 이미지 Push

Repository URI는 AWS CLI를 통해 조회하며 문서에 직접 기록하지 않습니다.

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

ECR 로그인과 Push에는 장기 Access Key를 사용하지 않습니다.

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

| 구분       | 대상 패키지                         | 대응 방식                         |
| -------- | ------------------------------ | ----------------------------- |
| glibc    | `libc6`, `libc-bin`, `locales` | `2.35-0ubuntu3.14` 이상으로 업데이트  |
| Wget     | `wget`                         | Runtime에서 사용하지 않아 제거          |
| Kerberos | `libkrb5-3` 외 관련 패키지           | `1.19.2-2ubuntu0.8` 이상으로 업데이트 |

Dockerfile에서는 glibc 및 Kerberos 관련 패키지 7개의 버전을 확인하며,
최소 보안 수정 버전에 미달하면 이미지 빌드를 실패시킵니다.

또한 Runtime 이미지에 `wget` 명령이 남아 있지 않은지 확인합니다.

대응 대상 취약점:

```text
CVE-2026-5450
CVE-2026-58469
CVE-2026-40355
CVE-2026-40356
```

## 9. 이미지 스캔 검증

Push된 이미지의 Media Type이 ECR Basic Scan을 지원하는
단일 이미지 Manifest인지 먼저 확인합니다.

지원 대상 예시:

```text
application/vnd.oci.image.manifest.v1+json
application/vnd.docker.distribution.manifest.v2+json
```

다음 형식은 ECR Basic Scan 대상 이미지로 사용하지 않습니다.

```text
application/vnd.oci.image.index.v1+json
```

스캔이 완료되면 이미지 태그, Digest, 완료 시각 및
심각도별 취약점 수를 함께 기록합니다.

```bash
aws ecr describe-image-scan-findings \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query '{
    Tag:imageId.imageTag,
    Digest:imageId.imageDigest,
    Status:imageScanStatus.status,
    CompletedAt:imageScanFindings.imageScanCompletedAt,
    Findings:imageScanFindings.findingSeverityCounts
  }' \
  --output json \
  --no-cli-pager
```

### 최종 검증 이미지

아래 값은 변경된 Dockerfile로 재빌드하고
ECR 스캔을 완료한 뒤 실제 결과로 교체합니다.

```text
Repository: otboo/backend 
Tag: manual-6b5ce7d-amd64 
Digest: sha256:6f1372fe6594b531f2c37565688bab2970ce060132a30bf476a780d950f80c1a 
Scan completed at: 2026-08-03T10:15:33+09:00
```

최종 스캔 결과:

```text
Status: COMPLETE 
CRITICAL: 0 
HIGH: 0 
MEDIUM: 14 
UNDEFINED: 2
```

## 10. Runtime 이미지 검증

최종 이미지에서 다음 항목을 확인합니다.

* OS: `linux`
* Architecture: `amd64`
* 실행 사용자: `otboo`
* `/app` 소유자: `otboo`
* Java 17 실행
* `javac` 미포함
* `/app/app.jar` 존재
* 이미지 태그와 OCI Label의 Commit 일치
* glibc 및 Kerberos 관련 패키지의 최소 수정 버전 적용
* Runtime 이미지에서 `wget` 제거
* CRITICAL 취약점 0건
* HIGH 취약점 0건

## 11. 이미지 태그 불변성 검증

원본과 Digest가 다른 이미지를 동일한 태그로 Push하여
`IMMUTABLE` 설정으로 Push가 차단되는지 확인합니다.

검증 전후 원격 이미지 Digest를 비교하여
기존 이미지가 변경되지 않았는지 확인합니다.

## 12. Pull 및 Digest 검증

로컬 ECR 태그 이미지를 삭제한 뒤
ECR에서 동일한 태그의 이미지를 다시 Pull합니다.

ECR에서 조회한 Image Digest와
Pull 이미지의 RepoDigest가 일치하는지 확인합니다.

## 13. Lifecycle Policy

Lifecycle Policy는 다음 파일에서 관리합니다.

```text
docs/aws/ecr/lifecycle-policy.json
```

적용 기준:

```text
미태그 이미지: Push 후 7일이 지나면 삭제
manual- 이미지: 최근 10개만 유지
```

정책 적용 전 Preview를 실행하여
예상하지 않은 이미지가 삭제 대상에 포함되지 않았는지 확인합니다.

정책 적용 후에는 ECR에 저장된 정책과
저장소의 `lifecycle-policy.json` 내용이 일치하는지 확인합니다.

운영 이미지에는 `manual-` 접두사를 사용하지 않습니다.

ECR 이미지를 수동으로 삭제하기 전에는
ECS Task Definition이 해당 이미지 태그 또는 Digest를 참조하는지 확인합니다.

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
linux/amd64 이미지 빌드
비루트 사용자 실행
/app 디렉터리 권한 설정
불필요한 Wget 패키지 제거
수정 가능한 CRITICAL 및 HIGH 취약점 대응
ECR 이미지 Push
이미지 스캔 결과와 Digest 연결
이미지 태그 불변성 확인
ECR 이미지 재Pull
Push·Pull Digest 일치 확인
Lifecycle Policy Preview 및 적용
AWS 인증정보 비노출 확인
```

Dockerfile 또는 이미지 빌드 방식이 변경된 경우에는
새 Git Commit SHA를 기준으로 이미지 빌드와 ECR 검증을 다시 수행합니다.

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
