# AWS 기본 운영 기준

## 1. 계정 및 리전

- 프로젝트: `otboo`
- AWS 기본 리전: `ap-northeast-2`
- 리전: 아시아 태평양(서울)
- AWS 인프라 담당: 팀 3 인프라 담당자

AWS 계정 ID, 계정 이메일, 사용자 ARN 등의 계정별 정보는
공개 저장소에 작성하지 않고 팀 내부에서만 관리합니다.

## 2. Root 계정 보안

- Root 계정에 MFA를 적용합니다.
- Root 계정 Access Key는 생성하지 않습니다.
- Root 계정은 계정 수준 설정 또는 계정 복구가 필요한 경우에만 사용합니다.
- 일상적인 콘솔 및 CLI 작업에는 IAM 사용자를 사용합니다.
- IAM 사용자 및 역할의 Billing 접근을 활성화했습니다.

## 3. IAM 사용자 및 권한

### 관리자 권한

- 관리자 그룹: `otboo-admins`
- 연결 정책: `AdministratorAccess`
- 구성원: 인프라 담당자 1명

프로젝트 초기 인프라 구축 기간에는 관리자 권한을
인프라 담당자 1명에게만 제한합니다.

인프라 구성이 안정화된 이후에는 실제 사용 권한을 기준으로
권한 축소 여부를 검토합니다.

### 일반 팀원

- 팀원별 개별 IAM 사용자를 사용합니다.
- 모든 IAM 사용자에게 MFA를 적용합니다.
- 공용 IAM 사용자는 사용하지 않습니다.
- 장기 Access Key는 생성하지 않습니다.
- 일반 팀원에게 관리자 권한을 부여하지 않습니다.
- 본인 비밀번호 변경을 위한 `IAMUserChangePassword` 정책만 유지합니다.
- 추가 권한은 실제 AWS 작업이 필요한 경우에만 부여합니다.

### 자동 배포 및 애플리케이션

- GitHub Actions용 IAM 사용자를 생성하지 않습니다.
- GitHub Actions 인증은 후속 CD 이슈에서 OIDC 기반 IAM Role로 구성합니다.
- ECS Task Execution Role과 ECS Task Role은 ECS 구성 이슈에서 생성합니다.
- 사람용 IAM 사용자의 인증정보를 GitHub Actions나 애플리케이션에서 사용하지 않습니다.

## 4. 비용 관리

- 프로젝트 Cost Budget: `US$150`
- 알림 기준: 실제 비용
- 실제 비용 50% 도달 시 이메일 알림
- 실제 비용 80% 도달 시 이메일 알림
- 실제 비용 100% 도달 시 이메일 알림
- Budget Action을 통한 자동 리소스 중지는 적용하지 않습니다.

AWS Budget 알림은 비용 발생을 즉시 차단하지 않습니다.
비용 집계와 알림에는 지연이 발생할 수 있으므로
Billing 콘솔에서도 비용 현황을 함께 확인합니다.

비용 알림을 받으면 다음 순서로 점검합니다.

1. Billing 또는 Cost Explorer에서 비용이 발생한 서비스를 확인합니다.
2. 예상하지 못한 리소스가 실행 중인지 확인합니다.
3. 개발과 시연에 필요하지 않은 유료 리소스를 중지하거나 삭제합니다.
4. 확인한 비용과 조치 내용을 팀 채널에 공유합니다.

## 5. AWS CLI 인증

- AWS CLI Version 2를 사용합니다.
- AWS CLI Profile 이름은 `otboo`를 사용합니다.
- 기본 리전은 `ap-northeast-2`를 사용합니다.
- `aws login`을 통한 임시 자격증명으로 인증합니다.
- Root 계정으로 AWS CLI에 로그인하지 않습니다.
- 장기 Access Key를 생성하거나 `aws configure`로 등록하지 않습니다.

로그인:

```bash
aws login --profile otboo
```

인증 주체 확인:

```bash
aws sts get-caller-identity --profile otboo
```

리전 확인:

```bash
aws configure get region --profile otboo
```

Profile 설정 확인:

```bash
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

| 환경 | 용도 |
| --- | --- |
| `dev` | 별도의 AWS 개발·검증 환경이 필요한 경우 |
| `prod` | 운영 및 시연 환경 |
| `shared` | 여러 환경에서 공통으로 사용하는 리소스 |

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

ECR Repository는 #46에서 다음 이름으로 구성합니다.

```text
otboo/backend
```

S3 버킷의 최종 이름은 #21에서 확정합니다.

## 7. 공통 태그 규칙

AWS 리소스를 생성할 때 가능한 경우 다음 태그를 적용합니다.

| 태그 키 | 값 | 용도 |
| --- | --- | --- |
| `Project` | `otboo` | 프로젝트 식별 |
| `Environment` | `dev`, `prod`, `shared` | 환경 구분 |
| `Owner` | `team3` | 관리 팀 식별 |
| `ManagedBy` | `manual` | 리소스 관리 방식 |
| `Purpose` | 리소스 용도 | 사용 목적 |

적용 예시:

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

- AWS Access Key
- AWS Secret Access Key
- AWS Session Token
- AWS 로그인 비밀번호
- AWS 계정 이메일
- 사용자 ARN
- 임시 인증 URL 및 인증 코드
- 실제 운영 환경변수 파일

AWS CLI Profile과 임시 인증정보는 프로젝트 폴더가 아닌
사용자 홈 디렉터리의 AWS CLI 설정 영역에서 관리합니다.

실제 인증정보를 Issue, PR, README 또는 팀 채널에 작성하지 않습니다.

## 9. 후속 AWS 작업

- #46: ECR Repository 생성 및 Docker 이미지 Push·Pull 검증
- #21: RDS PostgreSQL 및 S3 운영 환경 구성
- 별도 이슈: 운영 Redis 또는 ElastiCache 구성
- #22: ECS Cluster, Task Definition, Service 및 ALB 구성
- 후속 CD 이슈: GitHub Actions OIDC, ECR Push 및 ECS 자동 배포

#20에서는 ECR, RDS, S3, Redis, ECS 및 ALB 등의
애플리케이션 운영 리소스를 생성하지 않습니다.