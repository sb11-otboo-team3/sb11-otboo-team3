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

## 10. AWS 서비스별 문서

AWS 서비스별 상세 설정과 검증 절차는 하위 문서에서 관리합니다.

* [Amazon ECR 구성 및 이미지 검증](./ecr/README.md)
* [RDS PostgreSQL 및 S3 구성](./rds-s3/README.md)
* RDS PostgreSQL 및 S3 운영 환경: Issue #21에서 작성
* ECS 및 ALB 운영 환경: Issue #22에서 작성
* GitHub Actions OIDC 및 자동 배포: 후속 CD 이슈에서 작성

상위 문서에는 AWS 공통 운영 원칙만 작성하고,
서비스별 명령과 검증 결과는 각 하위 문서에서 관리합니다.
