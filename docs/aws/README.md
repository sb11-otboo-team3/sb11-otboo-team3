# AWS 기본 운영 기준

## 1. 계정 및 리전

* 프로젝트: `otboo`
* AWS 기본 리전: `ap-northeast-2`
* 리전 표시명: 아시아 태평양(서울)
* AWS 인프라 담당: 팀 3 인프라 담당자

AWS 계정 ID, 계정 이메일, 사용자 ARN 등의 계정 식별 정보는
공개 저장소에 작성하지 않고 필요한 인원만 확인합니다.

## 2. Root 계정 보안

* Root 계정에 MFA를 적용합니다.
* Root 계정 Access Key는 생성하지 않습니다.
* Root 계정은 계정 수준 설정 또는 계정 복구가 필요한 경우에만 사용합니다.
* 일상적인 AWS 콘솔 및 CLI 작업에는 IAM 사용자를 사용합니다.
* Root 계정 작업이 끝나면 즉시 로그아웃합니다.

## 3. IAM 사용자 및 권한

### Billing 접근

Root 계정에서 IAM 사용자 및 역할의 Billing 콘솔 접근 기능을
계정 수준으로 활성화했습니다.

이 설정은 IAM 사용자와 역할에 Billing 권한을 자동으로 부여하지 않습니다.
실제 Billing 및 Budget 접근은 별도의 IAM 권한을 가진
비용 관리 담당자만 사용할 수 있도록 제한합니다.

현재 비용 관리 담당자는 인프라 담당자이며,
일반 팀원에게는 Billing 관련 정책을 부여하지 않습니다.

### 관리자 권한

* 관리자 그룹: `otboo-admins`
* 연결 정책: `AdministratorAccess`
* 구성원: 인프라 담당자 1명
* 용도: 프로젝트 초기 AWS 인프라 구성

프로젝트 초기 인프라 구축 기간에는 관리자 권한을
인프라 담당자 1명에게만 제한합니다.

인프라 구성이 안정화된 이후에는 실제 작업에 필요한 권한을 기준으로
관리자 권한 축소 여부를 검토합니다.

### 일반 팀원

* 팀원별 개별 IAM 사용자를 사용합니다.
* 모든 IAM 사용자에게 MFA를 적용합니다.
* 공용 IAM 사용자는 사용하지 않습니다.
* 장기 Access Key는 생성하지 않습니다.
* 일반 팀원에게 관리자 권한을 부여하지 않습니다.
* 본인 비밀번호 변경용 `IAMUserChangePassword` 정책만 유지합니다.
* Billing 관련 정책은 부여하지 않습니다.
* 추가 권한은 실제 AWS 작업이 발생할 때 필요한 범위로만 부여합니다.

### 자동 배포 및 애플리케이션

* GitHub Actions 자동 배포용 IAM 사용자를 생성하지 않습니다.
* GitHub Actions 인증은 후속 CD 이슈에서 OIDC 기반 IAM Role로 구성합니다.
* ECS Task Execution Role과 ECS Task Role은 ECS 구성 이슈에서 생성합니다.
* 사람용 IAM 사용자의 인증정보를 GitHub Actions나 애플리케이션에서 사용하지 않습니다.

## 4. 비용 관리

* 프로젝트 Cost Budget: `US$50`
* 알림 기준: 실제 비용
* 실제 비용 50% 도달 시 이메일 알림
* 실제 비용 80% 도달 시 이메일 알림
* 실제 비용 100% 도달 시 이메일 알림
* Budget Action을 통한 자동 리소스 중지는 적용하지 않습니다.

AWS Budget 알림은 비용 발생을 즉시 차단하지 않습니다.
비용 집계와 알림에는 지연이 발생할 수 있으므로
Billing 콘솔에서도 비용 현황을 함께 확인합니다.

비용 알림을 받으면 다음 순서로 점검합니다.

1. Billing 또는 Cost Explorer에서 비용이 발생한 서비스를 확인합니다.
2. 예상하지 못한 리소스가 실행 중인지 확인합니다.
3. 개발과 시연에 필요하지 않은 유료 리소스를 중지하거나 삭제합니다.
4. 확인한 비용과 조치 내용을 팀 채널에 공유합니다.

## 5. AWS CLI 인증

### 사용 기준

* AWS CLI `2.32.0` 이상을 사용합니다.
* AWS CLI Profile 이름은 `otboo`를 사용합니다.
* 기본 리전은 `ap-northeast-2`를 사용합니다.
* `aws login`을 통한 임시 자격증명으로 인증합니다.
* Root 계정으로 AWS CLI에 로그인하지 않습니다.
* 장기 Access Key를 생성하거나 `aws configure`로 등록하지 않습니다.

`aws login`을 사용하는 IAM 사용자 또는 역할에는
`SignInLocalDevelopmentAccess` 관리형 정책에 포함된 권한이 필요합니다.

현재 인프라 담당자는 `AdministratorAccess`를 통해 해당 권한을 보유합니다.
향후 관리자 권한을 축소할 때는 `SignInLocalDevelopmentAccess`와
작업별 최소 권한 정책으로 분리합니다.

일반 팀원은 별도의 AWS CLI 작업이 필요하지 않은 동안
`SignInLocalDevelopmentAccess` 정책을 부여하지 않습니다.

### 확인 명령

```bash
aws --version
aws login --profile otboo
aws sts get-caller-identity --profile otboo
aws configure get region --profile otboo
aws configure list --profile otboo
```

명령 출력에 포함되는 AWS 계정 ID와 사용자 ARN은
Issue, PR, README 또는 팀 채널에 그대로 공유하지 않습니다.

## 6. 리소스 이름 규칙

기본 이름 형식:

```text
otboo-{environment}-{resource}
```

환경 구분:

| 환경       | 용도                         |
| -------- | -------------------------- |
| `dev`    | 별도의 AWS 개발 및 검증 환경이 필요한 경우 |
| `prod`   | 운영 및 시연 환경                 |
| `shared` | 여러 환경에서 공통으로 사용하는 리소스      |

대표 예시:

```text
otboo-prod-ecs-cluster
otboo-prod-ecs-service
otboo-prod-rds
otboo-prod-redis
otboo-prod-alb
```

AWS 서비스별 이름 제한이 다른 경우에는 기본 형식을 유지하면서
해당 서비스의 이름 규칙에 맞게 조정합니다.

ECR Repository는 Issue #46에서 다음 이름으로 구성했습니다.

```text
otboo/backend
```

S3 버킷의 최종 이름은 Issue #21에서 확정합니다.

## 7. 공통 태그 규칙

AWS 리소스를 생성할 때 가능한 경우 다음 태그를 적용합니다.

| 태그 키          | 값                       | 용도        |
| ------------- | ----------------------- | --------- |
| `Project`     | `otboo`                 | 프로젝트 식별   |
| `Environment` | `dev`, `prod`, `shared` | 환경 구분     |
| `Owner`       | `team3`                 | 관리 팀 식별   |
| `ManagedBy`   | `manual`                | 리소스 관리 방식 |
| `Purpose`     | 리소스 용도                  | 사용 목적     |

ECR 적용값:

```text
Project=otboo
Environment=shared
Owner=team3
ManagedBy=manual
Purpose=container-registry
```

태그 키와 값은 대소문자를 구분하므로 동일한 표기를 사용합니다.
태그에는 이메일, 비밀번호, Access Key 등의 민감정보를 작성하지 않습니다.

향후 관리 방식이 변경되면 `ManagedBy` 값을 변경할 수 있습니다.

```text
ManagedBy=github-actions
ManagedBy=terraform
```

## 8. 인증정보 관리

다음 정보는 GitHub 저장소에 포함하지 않습니다.

* AWS Access Key
* AWS Secret Access Key
* AWS Session Token
* AWS 로그인 비밀번호
* AWS 계정 이메일
* AWS 계정 ID
* 사용자 ARN
* 임시 인증 URL 및 인증 코드
* 실제 운영 환경변수 파일

AWS CLI Profile과 임시 인증정보는 프로젝트 폴더가 아닌
사용자 홈 디렉터리의 AWS CLI 설정 영역에서 관리합니다.

실제 인증정보를 Issue, PR, README 또는 팀 채널에 작성하지 않습니다.

Dockerfile과 Docker 이미지의 환경변수 및 History에도
AWS 인증정보가 포함되지 않도록 관리합니다.

## 9. AWS 작업 구분

* Issue #46: ECR Repository 구성 및 Docker 이미지 Push·Pull 검증
* Issue #21: RDS PostgreSQL 및 S3 운영 환경 구성
* 별도 이슈: 운영 Redis 또는 ElastiCache 구성
* Issue #22: ECS Cluster, Task Definition, Service 및 ALB 구성
* 후속 CD 이슈: GitHub Actions OIDC, ECR Push 및 ECS 자동 배포

Issue #46에서는 ECR Repository, 수동 이미지 Push·Pull,
취약점 스캔 및 Lifecycle Policy까지 구성합니다.

GitHub Actions 자동 Push와 ECS 배포는 Issue #46 범위에 포함하지 않습니다.

## 10. ECR Repository 구성

ECS에서 실행할 Docker 이미지를 저장하기 위해
`ap-northeast-2` 리전에 Private Repository를 구성했습니다.

적용 설정:

```text
Repository: otboo/backend
Repository 유형: Private
Image Tag Mutability: IMMUTABLE
Mutability 예외: 없음
암호화 방식: AES256
Registry Scan Type: BASIC
Scan Frequency: SCAN_ON_PUSH
```

Image Tag Mutability는 `IMMUTABLE`로 설정하여
이미 사용 중인 태그를 다른 이미지로 덮어쓸 수 없도록 했습니다.

Registry 수준의 `SCAN_ON_PUSH`를 적용하여
새로운 이미지가 Push될 때 자동으로 취약점 스캔이 실행됩니다.

## 11. Docker 이미지 기준

### 베이스 이미지

Builder와 Runtime Stage에는 Eclipse Temurin Java 17 Jammy 이미지를 사용합니다.

동일한 Git Commit을 다시 빌드했을 때 베이스 이미지가 임의로 변경되는 것을 줄이기 위해
Dockerfile의 베이스 이미지는 Tag와 Digest를 함께 지정합니다.

베이스 이미지 Digest를 변경할 때는 별도 Commit으로 관리하며,
변경 후 ECR 이미지 빌드와 취약점 스캔을 다시 수행합니다.

### 실행 사용자와 파일 권한

Runtime 이미지는 Root가 아닌 `otboo` 사용자로 실행합니다.

`/app` 디렉터리와 `/app/app.jar`는 모두 `otboo` 사용자가
접근하고 사용할 수 있도록 소유권을 설정합니다.

Runtime 이미지에는 Java 실행 환경만 포함하며
Java 컴파일러인 `javac`은 포함하지 않습니다.

### 이미지 원본 Commit

이미지에는 빌드 원본 Git Commit을 다음 OCI Label로 기록합니다.

```text
org.opencontainers.image.revision
```

이미지 태그와 Label은 같은 Git Commit을 기준으로 사용합니다.

## 12. 이미지 태그 규칙

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
* Dockerfile 또는 애플리케이션 코드 변경 후 먼저 Commit을 생성합니다.
* 이미지 태그의 SHA는 이미지에 포함된 코드와 Dockerfile의 Commit SHA를 의미합니다.
* 하나의 SHA와 Architecture 조합은 하나의 변경 불가능한 이미지를 의미합니다.
* `-r2`, `-r3` 등의 접미사 태그는 문제 분석용으로만 사용합니다.
* 문서만 변경된 경우 기존 이미지를 다시 빌드하지 않습니다.

## 13. 이미지 빌드 및 Push

이미지는 ECS 환경을 고려하여 `linux/amd64`로 빌드합니다.

이미지 태그의 Git Commit SHA와 실제 빌드 내용이 일치하도록,
빌드 전에 Docker 이미지에 영향을 주는 파일의 변경 여부를 확인합니다.

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

작업 트리가 깨끗한 경우 현재 Commit을 이미지 태그와
OCI Label의 공통 원본으로 사용합니다.

```bash
AWS_REGION="ap-northeast-2"
AWS_PROFILE="otboo"
ECR_REPOSITORY="otboo/backend"

IMAGE_SOURCE_COMMIT="$(git rev-parse HEAD)"
IMAGE_SOURCE_SHA="$(git rev-parse --short=7 "$IMAGE_SOURCE_COMMIT")"

IMAGE_TAG="manual-${IMAGE_SOURCE_SHA}-amd64"
LOCAL_IMAGE="otboo:${IMAGE_TAG}"
```

ECR Repository URI는 AWS CLI를 통해 조회하며
실제 URI는 공개 문서에 기록하지 않습니다.

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

Docker 이미지를 빌드합니다.

```bash
docker buildx build \
  --platform linux/amd64 \
  --build-arg IMAGE_SOURCE_COMMIT="$IMAGE_SOURCE_COMMIT" \
  --tag "$LOCAL_IMAGE" \
  --load \
  .
```

현재 수동 검증에서는 Build 결과를 로컬 Docker 저장소에 적재한 뒤
ECR에 Push합니다.

Provenance와 SBOM은 후속 GitHub Actions CD에서
이미지를 ECR로 직접 Push하는 방식으로 전환할 때 적용을 검토합니다.

ECR 로그인에는 Access Key나 비밀번호를 직접 작성하지 않고
`get-login-password`와 `--password-stdin`을 사용합니다.

```bash
aws ecr get-login-password \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  | docker login \
      --username AWS \
      --password-stdin "$ECR_REGISTRY"
```

이미지에 ECR 태그를 지정한 뒤 Push합니다.

```bash
docker tag "$LOCAL_IMAGE" "$ECR_IMAGE"
docker push "$ECR_IMAGE"
```

## 14. 취약점 대응

ECR Basic Scan에서 확인된 수정 가능한 CRITICAL 및 HIGH 취약점은
패키지의 실제 사용 여부와 수정 버전을 확인한 뒤 대응합니다.

전체 OS 패키지를 일괄 변경하는 `apt-get upgrade`는 사용하지 않습니다.

| 구분       | 대상 패키지                         | 대응 방식                         |
| -------- | ------------------------------ | ----------------------------- |
| glibc    | `libc6`, `libc-bin`, `locales` | `2.35-0ubuntu3.14` 이상으로 업데이트  |
| Wget     | `wget`                         | 런타임에서 사용하지 않아 제거              |
| Kerberos | `libkrb5-3` 외 관련 패키지           | `1.19.2-2ubuntu0.8` 이상으로 업데이트 |

Spring Actuator 기반 Health Check와 애플리케이션 기능에서
`wget`을 사용하지 않으므로 Runtime 이미지에서 제거합니다.

Dockerfile에서는 glibc 및 Kerberos 관련 패키지 7개의 버전을 각각 확인하며,
최소 보안 수정 버전에 미달하면 이미지 빌드를 실패시킵니다.

또한 `wget` 명령이 Runtime 이미지에 남아 있지 않은지 확인합니다.

대응한 취약점:

```text
CVE-2026-5450
CVE-2026-58469
CVE-2026-40355
CVE-2026-40356
```

## 15. 이미지 검증

### 최종 검증 이미지

최종 스캔 결과가 어떤 이미지에 대한 것인지 확인할 수 있도록
Repository, 이미지 태그, Digest 및 스캔 완료 시각을 함께 기록합니다.

아래 값은 변경된 Dockerfile로 이미지를 다시 빌드하고
ECR 스캔을 완료한 뒤 실제 결과로 갱신합니다.

```text
Repository: otboo/backend
Tag: manual-<GIT_SHORT_SHA>-amd64
Digest: sha256:<IMAGE_DIGEST>
Scan completed at: <UTC_TIMESTAMP>
```

스캔 결과는 다음 명령으로 조회합니다.

```bash
aws ecr wait image-scan-complete \
  --repository-name "$ECR_REPOSITORY" \
  --image-id imageTag="$IMAGE_TAG" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"

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

재검증 후 실제 스캔 결과를 기록합니다.

```text
Status: COMPLETE
CRITICAL: 0
HIGH: 0
MEDIUM: <재검증 결과>
UNDEFINED: <재검증 결과>
```

### Runtime 이미지 검증

최종 이미지에서 다음 항목을 확인합니다.

* OS: `linux`
* Architecture: `amd64`
* 실행 사용자: `otboo`
* `/app` 소유자: `otboo`
* Java 17 실행
* `javac` 미포함
* `/app/app.jar` 존재
* 이미지 태그와 원본 Commit Label 일치
* glibc 및 Kerberos 관련 패키지의 최소 수정 버전 적용
* Runtime 이미지에서 `wget` 제거
* CRITICAL 취약점 0건
* HIGH 취약점 0건

### 이미지 태그 불변성 검증

원본과 Digest가 다른 이미지를 동일한 태그로 Push하여
`IMMUTABLE` 설정으로 Push가 차단되는지 확인합니다.

불변성 검증 전후의 원격 이미지 Digest를 비교하여
기존 이미지가 변경되지 않았는지 확인합니다.

### 이미지 Pull 및 Digest 검증

로컬 ECR 태그 이미지를 삭제한 뒤
ECR에서 동일한 태그의 이미지를 다시 Pull합니다.

ECR에서 조회한 Image Digest와
Pull 이미지의 RepoDigest가 일치하는지 확인합니다.

## 16. Lifecycle Policy

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
`docs/aws/ecr/lifecycle-policy.json`의 내용이 일치하는지 확인합니다.

운영 이미지에는 `manual-` 접두사를 사용하지 않습니다.

ECR 이미지를 수동으로 삭제하기 전에는
ECS Task Definition이 해당 이미지 태그 또는 Digest를 참조하고 있는지 확인합니다.

## 17. Issue #46 완료 기준

Issue #46에서는 다음 항목을 완료 기준으로 사용합니다.

```text
ECR Private Repository 구성
공통 태그 적용
IMMUTABLE 설정
AES256 암호화 확인
BASIC 및 SCAN_ON_PUSH 적용
베이스 이미지 Digest 고정
Git Commit 기반 이미지 태그 적용
빌드 전 이미지 관련 작업 트리 검사
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

Dockerfile 또는 이미지 빌드 방식이 변경된 경우,
새 Git Commit SHA를 기준으로 이미지 빌드와 ECR 검증을 다시 수행합니다.

## 18. 후속 작업

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
