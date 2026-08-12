# ECS Fargate 및 ALB 최초 수동 배포

## 1. 문서 목적

이 문서는 `옷장을 부탁해` 애플리케이션을 AWS ECS Fargate에서 실행하고,
Application Load Balancer를 통해 외부에서 접근할 수 있도록 구성한
최초 수동 배포 환경과 검증 절차를 정리합니다.

React·Vite 프론트엔드 정적 리소스는 Spring Boot 애플리케이션에 포함하며,
ALB DNS의 루트 `/` 경로에서 프론트엔드와 백엔드 API를 함께 제공합니다.

이번 배포에서는 최초 배포의 정상 동작 확인을 우선합니다.

후속 이슈에서 다음 구성을 추가로 적용했습니다.

- Issue #131: GitHub Actions ECS 자동 배포
- Issue #142: Nginx Reverse Proxy
- Issue #157: 운영 도메인 DNS 연결

다음 항목은 후속 이슈에서 진행합니다.

- HTTPS 및 운영 Secure Cookie
- ECS 다중 Task
- 장시간 WebSocket·SSE 연결 및 재연결
- AWS 예상 비용 산정 및 비용 최적화

---

## 2. 관련 이슈 및 문서

- Issue #22: ECS Cluster, Task Definition, Service 및 ALB 구성
- Issue #131: GitHub Actions ECS 자동 배포
- Issue #142: Nginx Reverse Proxy
- Issue #157: 운영 도메인 DNS 연결
- [AWS 기본 운영 기준](../README.md)
- [Amazon ECR 구성 및 이미지 검증](../ecr/README.md)
- [RDS PostgreSQL 및 S3 구성](../rds-s3/README.md)
- [Amazon ElastiCache for Redis OSS 구성](../elasticache/README.md)

---

## 3. 프론트엔드 정적 리소스 통합

### 사용한 프론트엔드

```text
project-otboo-fe-2.0.7-release.zip
```

제공된 release의 `dist` 내부 파일을 Spring Boot의 정적 리소스 경로에 배치합니다.

```text
src/main/resources/static/
├── index.html
└── assets/
    ├── JavaScript
    ├── CSS
    ├── 이미지
    └── 폰트
```

Spring Boot 애플리케이션의 루트 `/` 경로에서 프론트엔드 화면을 제공합니다.

운영 Context Path로 `/sb/otboo`를 적용하지 않습니다.

프론트엔드와 API는 동일한 ALB Origin을 사용합니다.

확인한 Hash Router 경로는 다음과 같습니다.

```text
/#/recommendations
/#/closet
/#/feeds
/#/profiles
```

각 경로에서 브라우저 새로고침 후에도 404가 발생하지 않는 것을 확인했습니다.

### 프론트엔드 갱신 절차

```text
프론트엔드 소스 수정
→ 새 release 빌드
→ dist 내부 파일 확인
→ src/main/resources/static 교체
→ 로컬 정적 리소스 로딩 확인
→ 전체 테스트
→ bootJar 생성
→ Docker 이미지 빌드
→ ECR Push
→ 새 Task Definition Revision 등록
→ ECS Service 갱신
```

제공된 프론트엔드 API 요청과 전체 백엔드 Swagger 명세 비교는
각 팀원 도메인 구현 완료 후 별도 통합 검증 이슈에서 진행합니다.

---

## 4. 최초 배포 구조

```text
사용자
  ↓ HTTP 80
Application Load Balancer
  ↓ HTTP 8080
ECS Fargate Service
  ├─ RDS PostgreSQL
  ├─ ElastiCache Redis
  ├─ Amazon S3
  └─ CloudWatch Logs
```

네트워크 요청 흐름은 다음과 같습니다.

```text
인터넷
→ Public Subnet의 ALB
→ ALB Security Group
→ ECS Application Security Group
→ Public Subnet의 ECS Fargate Task
→ Private Subnet의 RDS PostgreSQL 및 Redis
```

최초 배포에서는 ECS Task를 Public Subnet에 배치하고 Public IP를 할당합니다.

RDS PostgreSQL과 ElastiCache Redis는 기존 Private Subnet 구성을 유지합니다.

---

## 5. ECS Cluster 및 Service 구성

| 구분 | 설정 |
| --- | --- |
| Cluster | `otboo-prod-cluster` |
| Service | `otboo-prod-backend-service` |
| 실행 방식 | Fargate |
| Desired Count | 1 |
| Public IP | 활성화 |
| Deployment Circuit Breaker | 활성화 |
| 배포 실패 자동 롤백 | 활성화 |
| Health Check Grace Period | 120초 |

정상 상태 기준은 다음과 같습니다.

```text
Service Status: ACTIVE
Desired Count: 1
Running Count: 1
Pending Count: 0
Deployment Rollout State: COMPLETED
Target Health: healthy
```

최초 배포는 Task 1개 구성입니다.

Task 교체 중 일시적인 접근 중단이 발생할 수 있으며,
이번 검증은 무중단 배포 검증을 의미하지 않습니다.

---

## 6. Task Definition 구성

| 구분 | 설정 |
| --- | --- |
| Family | `otboo-prod-backend` |
| 실행 방식 | Fargate |
| Network Mode | `awsvpc` |
| 운영체제 | Linux |
| CPU Architecture | X86_64 |
| CPU | 1 vCPU |
| Memory | 2 GB |
| Container Name | `otboo-backend` |
| Container Port | 8080 |
| Protocol | TCP |
| Application Protocol | HTTP |
| stopTimeout | 35초 |
| Log Driver | `awslogs` |

최초 등록 후 ALB Origin 설정을 반영한 Revision 2를 운영에 사용했습니다.

운영 이미지는 Git Commit SHA 태그를 사용합니다.

Docker 이미지에는 다음 source revision Label을 포함합니다.

```text
org.opencontainers.image.revision
```

Task Definition을 수정할 때 기존 Revision을 덮어쓰지 않습니다.

```text
Task Definition 수정
→ 새 Revision 등록
→ ECS Service의 Task Definition 변경
→ 새 Task 배포
→ 새 Target healthy 확인
→ 기존 Task 제거 확인
```

---

## 7. ALB 및 Target Group 구성

### Application Load Balancer

| 구분 | 설정 |
| --- | --- |
| 이름 | `otboo-prod-alb` |
| Scheme | internet-facing |
| Listener | HTTP 80 |
| Subnet | 서로 다른 가용 영역의 Public Subnet |
| Security Group | ALB 전용 Security Group |

### Target Group

| 구분 | 설정 |
| --- | --- |
| 이름 | `otboo-prod-backend-tg` |
| Target Type | `ip` |
| Protocol | HTTP |
| Port | 8080 |
| Health Check Path | `/actuator/health` |
| Health Check 성공 코드 | 200 |

Listener는 HTTP 80 요청을 Target Group으로 전달합니다.

Target Group은 ECS Task의 사설 IP와 8080 포트로 요청을 전달합니다.

---

## 8. Security Group 구성

### ALB Security Group

| Protocol | Port | Source |
| --- | ---: | --- |
| TCP | 80 | `0.0.0.0/0` |

SSH, RDP, 데이터베이스 포트 등 불필요한 관리 포트는 허용하지 않습니다.

### ECS Application Security Group

| Protocol | Port | Source |
| --- | ---: | --- |
| TCP | 8080 | ALB Security Group |

다음 Source는 ECS Application Security Group 인바운드에 포함하지 않습니다.

```text
0.0.0.0/0
개인 공인 IP
임의의 외부 Security Group
```

### RDS Security Group

| Protocol | Port | Source |
| --- | ---: | --- |
| TCP | 5432 | ECS Application Security Group |

### Redis Security Group

| Protocol | Port | Source |
| --- | ---: | --- |
| TCP | 6379 | ECS Application Security Group |

RDS와 Redis는 외부 인터넷에 직접 공개하지 않습니다.

---

## 9. Public Subnet과 Private Subnet

현재 최초 배포 구조는 다음과 같습니다.

```text
Public Subnet
├─ Application Load Balancer
└─ ECS Fargate Task + Public IP

Private Subnet
├─ RDS PostgreSQL
└─ ElastiCache Redis
```

ECS Task를 Private Subnet으로 이전하려면 다음 조건을 먼저 충족해야 합니다.

- NAT Gateway 또는 필요한 VPC Endpoint 구성
- ECR API 접근 경로 구성
- ECR Docker Registry 접근 경로 구성
- S3 접근 경로 구성
- CloudWatch Logs 접근 경로 구성
- SSM Parameter Store 접근 경로 구성
- Secrets Manager 접근 경로 구성
- Private Subnet Route Table 확인
- ECS Task Public IP 비활성화
- 새 Task 기동 확인
- ALB Target Health 확인

Private Subnet 이전은 별도 이슈에서 진행합니다.

---

## 10. 일반 환경변수와 Secret 분리

일반 설정값은 Task Definition의 `environment`로 전달합니다.

```text
SPRING_PROFILES_ACTIVE
SERVER_PORT
DB_HOST
DB_PORT
DB_NAME
REDIS_HOST
REDIS_PORT
Redis TLS 설정
AWS_REGION
S3_BUCKET
JWT 만료 시간
WEBSOCKET_ALLOWED_ORIGIN_PATTERNS
```

민감정보는 Task Definition의 `secrets`로 전달합니다.

```text
DB_USERNAME
DB_PASSWORD
REDIS_PASSWORD
JWT_SECRET
KAKAO_REST_API_KEY
KMA_API_KEY
```

RDS 사용자 정보는 Secrets Manager에서 관리합니다.

Redis AUTH Token, JWT Secret, Kakao REST API Key,
기상청 API Key는 Parameter Store에서 관리합니다.

실제 Secret 값은 다음 위치에 포함하지 않습니다.

- Git 저장소
- Issue
- Pull Request
- README
- Dockerfile
- Docker 이미지
- Task Definition의 평문 환경변수

---

## 11. IAM Role 구성

### ECS Task Execution Role

ECS가 Task를 시작할 때 필요한 AWS 작업을 담당합니다.

- ECR 이미지 Pull
- CloudWatch Logs 전송
- Secrets Manager Secret 조회
- Parameter Store Parameter 조회
- 필요한 KMS 복호화

Secret 조회 정책의 Resource는 필요한 Secret과 Parameter로 제한합니다.

### ECS Task Role

실행 중인 애플리케이션이 AWS 서비스에 접근할 때 사용합니다.

현재 Task Role은 S3 접근에 사용합니다.

허용한 주요 작업은 다음과 같습니다.

```text
s3:GetBucketLocation
s3:ListBucket
s3:GetObject
s3:PutObject
s3:DeleteObject
```

객체 접근은 다음 Prefix로 제한합니다.

```text
profiles/
clothes/
```

Task Role에는 `s3:*`와 같은 전체 권한을 부여하지 않습니다.

애플리케이션에는 고정 AWS Access Key를 주입하지 않습니다.

ECS Task Role의 임시 자격증명을 사용합니다.

---

## 12. RDS·Redis·S3 연결 구조

### RDS PostgreSQL

- Private Subnet에 배치
- 외부 공개 비활성화
- ECS Application Security Group에서 TCP 5432 접근
- Hikari Connection Pool 사용
- Flyway Migration으로 운영 스키마 적용
- 인증정보는 Secrets Manager에서 주입

### ElastiCache Redis

- Private Subnet에 배치
- ECS Application Security Group에서 TCP 6379 접근
- TLS 활성화
- AUTH Token 사용
- AUTH Token은 Parameter Store에서 주입

Redis TLS 연결과 AUTH 인증 오류 미발생까지 확인했습니다.

Redis를 사용하는 실제 기능의 저장·조회 검증은
해당 기능 구현 완료 후 진행합니다.

### Amazon S3

- ECS Task Role로 접근
- 고정 AWS Access Key 미사용
- 허용된 Prefix로 권한 제한
- Public Access Block 활성화
- HTTPS가 아닌 요청 거부
- 기본 서버 측 암호화 사용

프로필 및 의상 이미지의 실제 업로드·조회·삭제 검증은
각 도메인의 파일 저장 구현 완료 후 진행합니다.

---

## 13. CloudWatch Logs

운영 로그는 다음 Log Group으로 전송합니다.

```text
/ecs/otboo-prod
```

| 구분 | 설정 |
| --- | --- |
| Log Driver | `awslogs` |
| Region | `ap-northeast-2` |
| Stream Prefix | `backend` |
| 보존 기간 | 14일 |

운영 로그에는 다음 정보를 출력하지 않습니다.

- DB 비밀번호
- Redis AUTH Token
- JWT Secret
- 외부 API Key
- Access Token
- AWS 인증정보

애플리케이션 시작 로그에서 Spring Security의
자동 생성 비밀번호 문구가 확인되었습니다.

```text
Using generated security password:
```

해당 항목은 인증·인가 담당 후속 작업으로 분리했습니다.

수정 후 새 Task의 시작 로그에서 동일 문구가 더 이상 출력되지 않는지
다시 확인해야 합니다.

---

## 14. 최초 배포 상태 확인

### 공통 변수 설정

```bash
export AWS_REGION="ap-northeast-2"
export AWS_PROFILE="otboo"
export ECS_CLUSTER="otboo-prod-cluster"
export ECS_SERVICE="otboo-prod-backend-service"
export ALB_NAME="otboo-prod-alb"
```

실제 AWS 계정 ID와 ARN은 문서에 기록하지 않습니다.

### ECS Service 안정화 대기

```bash
aws ecs wait services-stable \
  --cluster "${ECS_CLUSTER}" \
  --services "${ECS_SERVICE}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}"
```

정상적으로 안정화되면 별도 출력 없이 터미널 프롬프트로 돌아옵니다.

### ECS Service 상태 확인

```bash
aws ecs describe-services \
  --cluster "${ECS_CLUSTER}" \
  --services "${ECS_SERVICE}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  --query 'services[0].{
    Status:status,
    DesiredCount:desiredCount,
    RunningCount:runningCount,
    PendingCount:pendingCount,
    TaskDefinition:taskDefinition,
    RolloutState:deployments[?status==`PRIMARY`]|[0].rolloutState,
    CircuitBreaker:deploymentConfiguration.deploymentCircuitBreaker.enable,
    Rollback:deploymentConfiguration.deploymentCircuitBreaker.rollback,
    HealthCheckGracePeriod:healthCheckGracePeriodSeconds
  }' \
  --output table
```

정상 기준:

```text
Status: ACTIVE
DesiredCount: 1
RunningCount: 1
PendingCount: 0
RolloutState: COMPLETED
CircuitBreaker: True
Rollback: True
HealthCheckGracePeriod: 120
```

### Target Group ARN 조회

```bash
TARGET_GROUP_ARN="$(
  aws ecs describe-services \
    --cluster "${ECS_CLUSTER}" \
    --services "${ECS_SERVICE}" \
    --region "${AWS_REGION}" \
    --profile "${AWS_PROFILE}" \
    --query 'services[0].loadBalancers[0].targetGroupArn' \
    --output text
)"
```

### ALB Target 상태 확인

```bash
aws elbv2 describe-target-health \
  --target-group-arn "${TARGET_GROUP_ARN}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  --query 'TargetHealthDescriptions[].{
    Target:Target.Id,
    Port:Target.Port,
    State:TargetHealth.State,
    Reason:TargetHealth.Reason
  }' \
  --output table
```

정상 기준:

```text
Port: 8080
State: healthy
```

### ALB DNS 조회

```bash
ALB_DNS="$(
  aws elbv2 describe-load-balancers \
    --names "${ALB_NAME}" \
    --region "${AWS_REGION}" \
    --profile "${AWS_PROFILE}" \
    --query 'LoadBalancers[0].DNSName' \
    --output text
)"
```

### 프론트엔드 및 Health Check 확인

```bash
curl -sS -o /dev/null \
  -w '메인 화면 HTTP %{http_code}\n' \
  "http://${ALB_DNS}/"

curl -sS "http://${ALB_DNS}/actuator/health"
echo
```

정상 결과:

```text
메인 화면 HTTP 200
{"status":"UP"}
```

### Swagger 및 OpenAPI 확인

```bash
curl -sS -o /dev/null \
  -w 'Swagger UI HTTP %{http_code}\n' \
  "http://${ALB_DNS}/swagger-ui/index.html"

curl -sS -o /dev/null \
  -w 'OpenAPI JSON HTTP %{http_code}\n' \
  "http://${ALB_DNS}/v3/api-docs"
```

### CloudWatch 로그 확인

```bash
aws logs tail "/ecs/otboo-prod" \
  --since 30m \
  --format short \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}"
```

---

## 15. AWS 콘솔 확인 경로

### ECS Service

```text
AWS Console
→ Elastic Container Service
→ Clusters
→ otboo-prod-cluster
→ Services
→ otboo-prod-backend-service
```

### Task Definition

```text
AWS Console
→ Elastic Container Service
→ Task definitions
→ otboo-prod-backend
```

### Application Load Balancer

```text
AWS Console
→ EC2
→ Load Balancers
→ otboo-prod-alb
```

### Target Group

```text
AWS Console
→ EC2
→ Target Groups
→ otboo-prod-backend-tg
```

### CloudWatch Logs

```text
AWS Console
→ CloudWatch
→ Log groups
→ /ecs/otboo-prod
```

### RDS PostgreSQL

```text
AWS Console
→ RDS
→ Databases
→ 운영 PostgreSQL 인스턴스
```

### ElastiCache Redis

```text
AWS Console
→ ElastiCache
→ Redis caches
→ 운영 Redis Replication Group
```

---

## 16. Task 장애 및 자동 복구 확인

ECS Service는 Desired Count를 유지합니다.

실행 중인 Task가 중지되면 ECS Service가 새 Task를 자동으로 생성합니다.

장애 복구 검증이 필요한 경우에만 현재 Task ARN을 조회합니다.

```bash
TASK_ARN="$(
  aws ecs list-tasks \
    --cluster "${ECS_CLUSTER}" \
    --service-name "${ECS_SERVICE}" \
    --desired-status RUNNING \
    --region "${AWS_REGION}" \
    --profile "${AWS_PROFILE}" \
    --query 'taskArns[0]' \
    --output text
)"
```

Task를 수동 중지합니다.

```bash
aws ecs stop-task \
  --cluster "${ECS_CLUSTER}" \
  --task "${TASK_ARN}" \
  --reason "Manual recovery validation" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}"
```

ECS Service가 다시 안정화될 때까지 기다립니다.

```bash
aws ecs wait services-stable \
  --cluster "${ECS_CLUSTER}" \
  --services "${ECS_SERVICE}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}"
```

Service 안정화 후 Target 상태를 다시 확인합니다.

```bash
aws elbv2 describe-target-health \
  --target-group-arn "${TARGET_GROUP_ARN}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  --query 'TargetHealthDescriptions[].{
    Target:Target.Id,
    Port:Target.Port,
    State:TargetHealth.State
  }' \
  --output table
```

새 Target의 상태가 `healthy`인지 확인합니다.

애플리케이션도 다시 확인합니다.

```bash
curl -sS -o /dev/null \
  -w '메인 화면 HTTP %{http_code}\n' \
  "http://${ALB_DNS}/"

curl -sS "http://${ALB_DNS}/actuator/health"
echo
```

정상 기준:

```text
Target State: healthy
메인 화면 HTTP 200
{"status":"UP"}
```

복구 완료 기준은 다음과 같습니다.

```text
새 Task 자동 생성
새 Task RUNNING
Desired Count와 Running Count 일치
Pending Count 0
새 Target 자동 등록
새 Target healthy
기존 Target 제거
/actuator/health 상태 UP
프론트엔드 HTTP 200
```

최초 배포 검증에서 다음 결과를 확인했습니다.

- 기존 Task 수동 중지
- 새 Task 자동 생성
- 새 Task RUNNING
- 새 Target 자동 등록
- 새 Target `healthy`
- 기존 Target 제거
- 기존 Task 최종 `STOPPED`
- 재기동 후 프론트엔드 HTTP 200
- 재기동 후 `/actuator/health` 상태 `UP`

Task 1개 구성에서는 교체 과정에서 일시적인 접근 중단이 발생할 수 있습니다.

이 검증은 Service 자동 복구 검증이며,
Rolling Update 또는 무중단 배포 검증을 의미하지 않습니다.

---

## 17. Secret 변경 시 새 Task 배포 기준

Secrets Manager 또는 Parameter Store의 값을 변경해도
이미 실행 중인 ECS Task의 환경변수는 자동으로 갱신되지 않습니다.

Secret 변경 후 다음 순서로 새 Task를 실행합니다.

```text
Secret 또는 Parameter 값 변경
→ 변경 상태 확인
→ ECS Service 새 배포 실행
→ 새 Task에 변경값 주입
→ 새 Target healthy 확인
→ 기존 Task 제거 확인
→ 애플리케이션 기능 재검증
```

Task Definition의 변수명, Secret 경로 또는 IAM 권한이 변경되면
새 Task Definition Revision을 등록해야 합니다.

Secret 값만 변경되고 Task Definition 구조가 동일한 경우에도
새 Task가 시작되어야 변경값이 적용됩니다.

---

## 18. 운영 리소스 종료 순서

운영 종료가 확정된 경우에만 다음 순서로 정리합니다.

```text
1. ECS Service Desired Count를 0으로 변경
2. 실행 중인 ECS Task 종료 확인
3. ECS Service 삭제
4. ALB Listener 삭제
5. Application Load Balancer 삭제
6. Target Group 삭제
7. CloudWatch Log Group 보존 여부 결정 후 삭제
8. 사용하지 않는 Task Definition Revision 비활성화
9. ECS Cluster에 남은 Service와 Task 확인
10. ECS Cluster 삭제
```

RDS, Redis, S3, ECR, Secrets Manager 및 Parameter Store는
다른 환경과 이슈에서 사용 중인지 확인한 뒤 별도로 정리합니다.

데이터가 저장된 RDS와 S3는 백업 및 보존 정책을 확인하기 전에 삭제하지 않습니다.

AWS 예상 비용 산정과 비용 최적화는 별도 이슈에서 진행합니다.

---

## 19. 최초 배포 검증 결과

### 완료된 검증

- 프론트엔드 정적 리소스가 포함된 Docker 이미지 ECR Push
- Git Commit SHA 이미지 태그 적용
- Docker 이미지 source revision Label 확인
- ECS Task `RUNNING`
- ECS Service `ACTIVE`
- Desired Count와 Running Count 일치
- Pending Count 0
- Deployment `COMPLETED`
- ALB Target `healthy`
- ALB DNS 루트 경로 프론트엔드 HTTP 200
- JavaScript·CSS·SVG·폰트 정상 로딩
- Hash Router 경로 새로고침
- `/actuator/health` HTTP 200 및 `UP`
- Swagger UI 접근
- OpenAPI JSON 접근
- RDS PostgreSQL 연결
- Hikari Connection Pool 시작
- Flyway Migration 적용
- 대표 생성·조회·수정 API의 DB 반영
- Redis TLS 연결
- Redis AUTH 인증 오류 미발생
- ECS Task Role S3 최소 권한 적용
- S3 Prefix 제한
- 고정 AWS Access Key 미사용
- WebSocket Handshake
- SSE 기본 연결
- ECS Task 중지 후 Service 자동 복구
- 새 ALB Target `healthy`
- 기존 Target 제거
- CloudWatch Logs 수집
- CloudWatch Logs 보존 기간 14일

---

## 20. GitHub Actions ECS 자동 배포

Issue #131에서는 Issue #124에서 구성한 ECR 자동 Push 이후
Git SHA 이미지를 기준으로 ECS Task Definition Revision을 생성하고
ECS Service를 자동으로 업데이트하는 배포 흐름을 구성합니다.

### 자동 배포 흐름

```text
develop Push
→ GitHub Actions ECR 이미지 Build 및 Push
→ ECR Push Job 성공
→ ECS Deploy Job 시작
→ 현재 ECS Service의 Task Definition 조회
→ 현재 otboo-backend 컨테이너 이미지 확인
→ Git SHA 기반 ECR 이미지와 비교
→ 이미지가 다른 경우 Task Definition 렌더링
→ 새 Task Definition Revision 등록
→ ECS Service 업데이트
→ Service Stable 상태까지 대기
```

ECR 이미지 Build·Push가 성공한 이후에만
ECS 배포 Job이 실행되도록 `needs: build-and-push` 의존성을 구성합니다.

ECR Push와 ECS 배포를 별도의 독립적인 Workflow로 동시에 실행하지 않아
아직 생성되지 않은 이미지를 ECS가 먼저 참조하는 상황을 방지합니다.

### ECS 배포 대상

```text
AWS Region
ap-northeast-2

ECS Cluster
otboo-prod-cluster

ECS Service
otboo-prod-backend-service

Task Definition Family
otboo-prod-backend

Container Name
otboo-backend

Container Port
8080
```

자동 배포에서는 현재 ECS Service가 사용 중인 Task Definition을 기준으로
기존 환경변수, Secret, 로그, CPU·Memory, Task Role 및 Execution Role 설정을 유지하고
`otboo-backend` 컨테이너의 이미지 URI만 새로운 Git SHA 이미지로 변경합니다.

### GitHub OIDC ECS Deploy Role

ECR Build·Push Role과 ECS Deploy Role을 분리합니다.

```text
ECR Build / Push
otboo-github-actions-ecr-role

ECS Deploy
otboo-github-actions-ecs-deploy-role
```

ECS Deploy Role 역시 장기 AWS Access Key를 사용하지 않고
GitHub OIDC를 통해 임시 자격증명을 발급받습니다.

Trust Policy는 다음 조건으로 제한합니다.

```text
Audience
sts.amazonaws.com

Repository / Branch
sb11-otboo-team3/sb11-otboo-team3
develop
```

GitHub Repository Variable은 다음 값을 사용합니다.

```text
AWS_GITHUB_ACTIONS_ECS_DEPLOY_ROLE_ARN
```

IAM Role ARN은 Secret 값이 아니므로 Repository Variable로 관리하고,
실제 AWS 인증은 GitHub OIDC Trust Policy를 통해 수행합니다.

### ECS Deploy 최소 권한

`otboo-github-actions-ecs-deploy-role`에는
자동 배포에 필요한 권한만 부여합니다.

```text
ecs:DescribeTaskDefinition
ecs:RegisterTaskDefinition
ecs:DescribeServices
ecs:UpdateService
ecs:ListTasks
ecs:DescribeTasks
elasticloadbalancing:DescribeTargetHealth
elasticloadbalancing:DescribeLoadBalancers
iam:PassRole
```

`iam:PassRole`은 다음 두 Role만 허용합니다.

```text
otboo-prod-ecs-task-role
otboo-prod-ecs-task-execution-role
```

또한 ECS Task에 Role을 전달하는 경우로 제한합니다.

```text
iam:PassedToService = ecs-tasks.amazonaws.com
```

`ecs:UpdateService`와 `ecs:DescribeServices`는
`otboo-prod-backend-service`를 대상으로 제한합니다.

### 동일 Git SHA 재실행 처리

현재 ECS Service가 이미 같은 Git SHA 이미지를 사용하고 있는지
배포 전에 확인합니다.

```text
현재 ECS Image == Target Git SHA Image
→ 새 Task Definition Revision 생성하지 않음
→ ECS Service Update 생략
→ Workflow 정상 종료
```

이미 동일한 이미지가 배포된 상황에서
불필요한 Task Definition Revision과 재배포가 반복되지 않도록 합니다.

### Workflow 동시 실행 기준

ECR Push와 ECS 배포가 하나의 Workflow에서 이어지므로
배포가 진행 중인 Workflow를 새로운 Push가 강제로 취소하지 않도록 구성합니다.

```yaml
concurrency:
  group: ecr-push-${{ github.ref }}
  cancel-in-progress: false
```

현재 실행 중인 배포는 새로운 Push로 취소하지 않습니다.

기본 concurrency 정책에서는 동일 Group의 pending 실행을 하나만 유지하므로,
배포 중 develop Push가 여러 번 발생하면 기존 pending 실행은 취소되고
가장 최신 pending 실행으로 대체될 수 있습니다.

따라서 실행 중인 배포를 완료한 뒤 가장 최신 Git SHA를 후속 배포하는 정책으로 운영합니다.

### 자동 배포 검증 기준

Issue #131은 `develop` Merge 후 실제 Workflow에서 다음 항목을 검증합니다.

```text
GitHub OIDC ECS Deploy Role 인증 성공
ECR Build 및 Push Job 성공 후 ECS Deploy Job 실행
현재 ECS Service Task Definition 조회 성공
현재 컨테이너 이미지 조회 성공
새 Git SHA 이미지 URI 생성 확인
새 Task Definition Revision 등록
ECS Service가 새 Revision으로 업데이트
Service Stable 대기 성공
Desired Count와 Running Count 일치
Pending Count 0
새 Task RUNNING
ALB Target healthy
/actuator/health HTTP 200 및 UP
실행 Task의 이미지가 대상 Git SHA와 일치
CloudWatch Logs 정상 수집
동일 Workflow 재실행 시 불필요한 ECS 재배포 생략
```

Rolling Update 정책 조정, 무중단 배포 보장 및 실패 배포 자동 롤백은
Issue #131의 범위에 포함하지 않고 별도 운영 안정화 이슈에서 진행합니다.

---

## 21. Nginx Reverse Proxy 운영 구성

Issue #142에서는 기존 ALB가 Spring Boot 애플리케이션의 8080 포트로
직접 요청을 전달하던 구조에 Nginx Reverse Proxy를 추가했습니다.

Issue #142에서는 도메인 DNS 연결과 HTTPS 적용을 범위에서 제외했습니다.

도메인 DNS 연결은 후속 Issue #157에서 적용했으며,
HTTPS 및 운영 Secure Cookie는 별도 후속 이슈에서 진행합니다.

### 적용 전 구조

```text
Internet
→ ALB :80
→ ECS Task
→ otboo-backend :8080
→ Spring Boot
```

### 적용 후 구조

```text
Internet
→ ALB :80
→ ECS Task
   ├─ otboo-nginx :80
   │    ↓ 127.0.0.1:8080
   └─ otboo-backend :8080
```

Nginx와 Spring Boot는 동일한 ECS Fargate Task 안에서
서로 다른 컨테이너로 실행합니다.

Nginx는 외부 요청을 받는 Reverse Proxy 역할을 담당하고,
Spring Boot는 Task 내부의 8080 포트에서 애플리케이션 요청을 처리합니다.

### Sidecar 구조 선택

Nginx와 Spring Boot의 실행 책임을 분리하기 위해
하나의 컨테이너에 두 프로세스를 함께 실행하지 않고 별도 컨테이너로 구성했습니다.

현재 서비스 규모에서는 Nginx만을 위한 별도 ECS Service와
Service Discovery 구성을 추가할 필요가 없다고 판단하여
동일 ECS Task의 Sidecar 구조를 사용합니다.

동일 Task의 컨테이너은 다음 경로로 통신합니다.

```text
otboo-nginx :80
→ http://127.0.0.1:8080
→ otboo-backend
```

ECS Task가 교체될 때 Nginx와 Spring Boot도
하나의 배포 단위로 함께 생성되고 제거됩니다.

### Nginx 이미지

Nginx 이미지는 별도 ECR Repository에서 관리합니다.

```text
otboo/nginx
```

Nginx 설정 또는 Dockerfile이 변경되어 새 이미지를 생성하는 경우
해당 변경의 Git Commit SHA를 이미지 태그로 사용합니다.

```text
otboo/nginx:{nginx-change-git-sha}
```

Backend 자동 배포에서는 Nginx 이미지를 매번 다시 빌드하지 않습니다.

GitHub Actions는 현재 ECS Service가 사용하는 Task Definition을 조회한 뒤
`otboo-backend` 컨테이너 이미지만 새로운 Git SHA 이미지로 변경합니다.

따라서 현재 Task Definition에 포함된 `otboo-nginx` 컨테이너와
Nginx 이미지 태그는 Backend 자동 배포 시 그대로 유지됩니다.

Nginx 설정 또는 Dockerfile이 변경되는 경우에는
Nginx 이미지를 새 Git Commit SHA로 다시 빌드·Push하고
Task Definition의 `otboo-nginx` 이미지도 함께 갱신합니다.

| 구분 | 설정 |
| --- | --- |
| Image Tag Mutability | IMMUTABLE |
| Scan On Push | 활성화 |
| Encryption | AES256 |
| Build Platform | linux/amd64 |

### ECS Task Definition 구성

Nginx 적용 후 하나의 Task Definition에
다음 두 컨테이너가 포함됩니다.

| 컨테이너 | 포트 | 역할 |
| --- | ---: | --- |
| `otboo-nginx` | 80 | Reverse Proxy |
| `otboo-backend` | 8080 | Spring Boot 애플리케이션 |

Task 전체 리소스는 기존 설정을 유지합니다.

```text
CPU: 1 vCPU
Memory: 2 GB
Network Mode: awsvpc
Runtime: Linux / X86_64
```

Backend 컨테이너의 기존 환경변수, Secret, Task Role,
Execution Role 및 CloudWatch Logs 설정은 그대로 유지합니다.

Nginx 로그는 기존 Log Group을 사용하고
별도 Stream Prefix로 구분합니다.

```text
Log Group
/ecs/otboo-prod

Backend Stream Prefix
backend

Nginx Stream Prefix
nginx
```

### Nginx Reverse Proxy 설정

공통 요청은 Spring Boot의 8080 포트로 전달합니다.

```nginx
proxy_pass http://127.0.0.1:8080;
```

다음 Forwarded Header를 전달합니다.

```text
Host
X-Real-IP
X-Forwarded-For
X-Forwarded-Host
X-Forwarded-Proto
```

Spring Boot 운영 환경에서는 Forwarded Header를 처리하도록
다음 설정을 적용합니다.

```yaml
server:
  forward-headers-strategy: framework
```

### WebSocket 및 SockJS 프록시 설정

WebSocket Endpoint는 다음 경로를 사용합니다.

```text
/ws
```

Nginx는 `/ws` 요청에 대해 WebSocket Upgrade Header 전달과
Streaming 응답 Buffering 비활성화를 적용합니다.

```nginx
proxy_buffering off;
proxy_read_timeout 65m;
```

Issue #142의 기능 검증 범위에서는
Native WebSocket Upgrade 요청을 검증했습니다.

로컬 검증에서 Spring Boot 직접 요청과 Nginx 경유 요청 모두
다음 응답을 확인했습니다.

```text
101 Switching Protocols
```

애플리케이션의 `/ws` Endpoint에는 SockJS가 구성되어 있지만,
SockJS HTTP fallback transport인 `xhr_streaming` 요청은
Spring Boot에 직접 요청한 경우에도 Spring Security CSRF 정책으로
403 응답하는 것을 확인했습니다.

따라서 현재 Issue #142에서는 Native WebSocket Reverse Proxy 동작까지만
검증 범위에 포함하며, SockJS HTTP fallback의 정상 지원은 포함하지 않습니다.

SockJS fallback을 지원하기 위한 Spring Security 및 CSRF 정책은
인증·인가 및 WebSocket 기능 담당 범위에서 별도로 검토합니다.

CSRF는 전역으로 비활성화하지 않습니다.

### Server-Sent Events

SSE Endpoint는 다음 경로를 사용합니다.

```text
/api/sse
```

SSE 응답이 즉시 전달되도록 다음 설정을 적용합니다.

```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 65m;
```

로컬 검증에서 인증된 Nginx 경유 요청이
다음과 같이 정상 연결되는 것을 확인했습니다.

```text
HTTP 200
Content-Type: text/event-stream

event: connected
data: SSE 연결이 완료되었습니다.
```

### ALB 및 Target Group 전환

기존 ECS Service의 ALB 연결은 다음과 같았습니다.

```text
Container Name: otboo-backend
Container Port: 8080
Registered Target Port: 8080
```

Nginx 적용 후 ECS Service의 Load Balancer 연결을
다음과 같이 변경했습니다.

```text
Container Name: otboo-nginx
Container Port: 80
Registered Target Port: 80
```

기존 Target Group `otboo-prod-backend-tg`는 그대로 사용합니다.

Target Group 리소스에 설정된 기본 Port는 기존 값인 8080을 유지하지만,
ECS Service가 Target을 등록할 때 `otboo-nginx`의 `containerPort: 80`을
사용하므로 실제 등록 Target은 ECS Task IP의 80 포트입니다.

```text
Target Group Default Port
8080

ECS Service Load Balancer Mapping
otboo-nginx :80

Actual Registered Target
<ECS Task Private IP>:80
```

Target Group Health Check는 다음 설정을 사용합니다.

```text
Health Check Port
traffic-port

Health Check Path
/actuator/health

Success Code
200
```

`traffic-port`를 사용하므로 Nginx 전환 후 실제 Health Check 포트는
등록 Target과 동일한 TCP 80입니다.

최종 Health Check 요청 흐름은 다음과 같습니다.

```text
ALB
→ ECS Target :80
→ otboo-nginx :80
→ 127.0.0.1:8080
→ otboo-backend
→ /actuator/health
```

ECS Application Security Group 역시
ALB Security Group으로부터 TCP 80만 허용합니다.

기존 ALB → ECS TCP 8080 인바운드 규칙은
Nginx Target이 `healthy`가 되고 기존 8080 Target이 제거된 이후 삭제했습니다.

### Security Group 변경

전환 전 ECS Application Security Group은
ALB Security Group에서 TCP 8080 접근을 허용했습니다.

Nginx 전환 과정에서는 기존 Task의 트래픽을 유지하기 위해
8080 규칙을 먼저 제거하지 않고 TCP 80 규칙을 추가했습니다.

```text
전환 중

ALB Security Group
├─ TCP 8080 → 기존 Backend Target
└─ TCP 80   → 신규 Nginx Target
```

새 Nginx Target이 `healthy`가 되고
기존 Backend 8080 Target이 완전히 제거된 이후
ALB → ECS TCP 8080 인바운드 규칙을 제거했습니다.

최종 외부 애플리케이션 진입 경로는 다음과 같습니다.

```text
ALB Security Group
→ TCP 80
→ ECS Application Security Group
→ otboo-nginx :80
```

Spring Boot의 8080 포트는 Nginx가 동일 Task 내부에서 사용하는
애플리케이션 Upstream 포트로 유지합니다.

### 운영 배포 절차

Nginx Reverse Proxy 최초 적용은 다음 순서로 진행합니다.

```text
1. Nginx 설정 작성 및 로컬 검증
2. Nginx Docker 이미지 linux/amd64 빌드
3. Git Commit SHA 태그로 ECR Push
4. 기존 Task Definition을 기준으로 Nginx Sidecar 추가
5. 새 Task Definition Revision 등록
6. ALB Security Group → ECS Security Group TCP 80 허용
7. ECS Service의 Task Definition 변경
8. ALB Target Container를 otboo-nginx:80으로 변경
9. 새 ECS Task RUNNING 확인
10. 새 :80 Target healthy 확인
11. 기존 :8080 Target draining 확인
12. ECS Service Stable 확인
13. 운영 기능 및 로그 검증
14. 기존 :8080 Target 제거 확인
15. 기존 ALB → ECS TCP 8080 Security Group 규칙 제거
16. 최종 Target Health 및 /actuator/health 재검증
```

### Rolling 전환 확인

서비스는 다음 배포 정책을 사용합니다.

```text
Desired Count: 1
minimumHealthyPercent: 100
maximumPercent: 200
Deployment Strategy: ROLLING
Deployment Circuit Breaker: enabled
Rollback: enabled
```

Nginx 적용 과정에서는 기존 Task를 유지한 상태에서
새 Task가 추가로 실행되는 것을 확인했습니다.

```text
기존 Task
otboo-backend :8080
healthy

        ↓ 새 Task 시작

신규 Task
otboo-nginx :80
→ otboo-backend :8080
healthy

        ↓

기존 :8080 Target
draining

        ↓

기존 Target 제거
```

새 Target이 `healthy`가 된 이후 기존 Target이
`draining` 상태로 전환되고 제거되는 것을 확인했습니다.

### 운영 검증 결과

다음 항목을 실제 운영 환경에서 확인했습니다.

- ECS Service Desired Count 1
- ECS Service Running Count 1
- Pending Count 0
- Deployment `COMPLETED`
- ALB Target `otboo-nginx:80`
- Target Health `healthy`
- `/actuator/health` HTTP 200 및 `UP`
- 응답 Header에서 `Server: nginx/1.30.4` 확인
- 루트 `/` HTTP 200
- JavaScript 정적 리소스 `/assets/**` HTTP 200
- `/api/auth/csrf-token` HTTP 204
- Nginx Access Log 정상 수집
- Spring Boot 시작 로그 정상 수집
- 기존 Backend `:8080` Target 제거
- 기존 ALB → ECS TCP 8080 Security Group 규칙 제거
- TCP 8080 규칙 제거 후에도 `/actuator/health` HTTP 200 유지

최종 외부 요청 흐름은 다음과 같습니다.

```text
Internet
→ ALB :80
→ ECS Application Security Group :80
→ otboo-nginx :80
→ 127.0.0.1:8080
→ otboo-backend
```

### 컨테이너 시작 시 Health Check

Nginx와 Backend는 동일 Task에서 함께 시작하지만
별도 컨테이너이므로 준비 완료 시점은 다를 수 있습니다.

최초 Sidecar 배포에서는 Nginx가 먼저 시작되어
Spring Boot가 8080 포트에서 준비되기 전
ALB Health Check에 일시적인 502 응답이 발생했습니다.

Spring Boot 기동 완료 후에는 Nginx를 경유한
Health Check가 200으로 정상 전환되었고
Target 상태도 `healthy`로 변경되었습니다.

현재는 ALB Target Health Check를 통해
애플리케이션 준비 완료 여부를 판단합니다.

### 롤백 절차

Nginx 적용 후 문제가 발생하면 다음 순서로 롤백합니다.

```text
1. ALB Security Group → ECS Security Group TCP 8080 허용
2. 이전 Backend 전용 Task Definition Revision 선택
3. Load Balancer Target Container를 otboo-backend:8080으로 복원
4. ECS Service 업데이트
5. 새 Backend Target healthy 확인
6. 기존 Nginx Target 제거 확인
7. 프론트엔드와 /actuator/health 재검증
```

정상 Target이 확보되기 전에
현재 정상 동작 중인 Target이나 Security Group 규칙을
먼저 제거하지 않습니다.

### 후속 작업

다음 항목은 Nginx Reverse Proxy 이슈에 포함하지 않습니다.

- HTTPS 및 ACM 인증서 적용
- HTTP → HTTPS Redirect
- Secure Cookie 운영 검증
- SockJS HTTP fallback CSRF 정책 검토
- 장시간 WebSocket·SSE 연결 및 재연결 검증
- ECS 다중 Task 구성

### 후속 검증

다음 항목은 팀원 도메인 구현 또는 후속 인프라 이슈에서 진행합니다.

- 프론트엔드와 백엔드 전체 API 명세 비교
- Redis 사용 기능의 실제 저장·조회
- 프로필 및 의상 이미지 업로드·조회·삭제
- WebSocket 실제 메시지 송수신
- Spring Security 기본 사용자 자동 생성 로그 제거
- 장시간 WebSocket·SSE 연결 및 재연결
- ECS 다중 Task
- 무중단 배포
- HTTPS 및 운영 Secure Cookie

## 22. Route 53 운영 도메인 DNS 연결

Issue #157에서는 운영 및 시연에 사용할 도메인 `otboo.work`를 등록하고,
Route 53 Alias A 레코드를 통해 기존 운영 ALB인 `otboo-prod-alb`와 연결했습니다.

이번 이슈의 범위는 도메인 등록·DNS 연결과
HTTP 환경에서 기존 ALB → Nginx → Spring Boot 요청 경로가
정상적으로 유지되는지 확인하는 것까지입니다.

ACM 인증서, HTTPS Listener, HTTP → HTTPS Redirect 및
Secure Cookie 적용은 후속 HTTPS 이슈에서 진행합니다.

### 최종 요청 구조

```text
Internet
→ otboo.work
→ Route 53 Alias A
→ otboo-prod-alb :80
→ otboo-nginx :80
→ 127.0.0.1:8080
→ otboo-backend
```

Route 53 연결 과정에서 기존 ALB Target,
ECS Service와 Nginx Sidecar 구조는 변경하지 않았습니다.

### 도메인 등록 및 Nameserver 검증

운영 도메인은 다음과 같이 확정했습니다.

```text
otboo.work
```

Route 53 Domains 관련 명령은 `us-east-1` 리전에서 실행합니다.

등록된 도메인의 Nameserver를 확인합니다.

```bash
aws route53domains get-domain-detail \
  --domain-name otboo.work \
  --region us-east-1 \
  --profile otboo \
  --query "Nameservers[].Name" \
  --output table
```

도메인 등록 완료 후 Nameserver 4개가 정상적으로 할당된 것을 확인했습니다.

Route 53에서 `otboo.work`와 이름이 일치하고
`PrivateZone=False`인 Public Hosted Zone만 조회합니다.

```bash
HOSTED_ZONE_IDS="$(
  aws route53 list-hosted-zones-by-name \
    --dns-name otboo.work \
    --profile otboo \
    --query "HostedZones[?Name=='otboo.work.' && Config.PrivateZone==\`false\`].Id" \
    --output text
)"
```

동일한 이름의 Public Hosted Zone이 여러 개 존재하면
잘못된 Hosted Zone을 선택할 수 있으므로 조회 결과가 정확히 1개인지 확인합니다.

```bash
HOSTED_ZONE_COUNT="$(
  printf '%s\n' "${HOSTED_ZONE_IDS}" \
    | awk 'NF { count += NF } END { print count + 0 }'
)"

test "${HOSTED_ZONE_COUNT}" -eq 1
```

`list-hosted-zones-by-name`에서 반환되는 Hosted Zone ID는
`/hostedzone/Z...` 형식이므로 이후 CLI 명령에서 사용할 수 있도록
`/hostedzone/` 접두사를 제거해 `HOSTED_ZONE_ID`를 설정합니다.

```bash
HOSTED_ZONE_ID="${HOSTED_ZONE_IDS##*/}"

printf 'HOSTED_ZONE_ID=%s\n' "${HOSTED_ZONE_ID}"
```

설정된 Hosted Zone이 실제로 `otboo.work`의 Public Hosted Zone인지 다시 확인합니다.

```bash
aws route53 get-hosted-zone \
  --id "${HOSTED_ZONE_ID}" \
  --profile otboo \
  --query 'HostedZone.{Id:Id,Name:Name,PrivateZone:Config.PrivateZone}' \
  --output table
```

확인 기준은 다음과 같습니다.

```text
Name        = otboo.work.
PrivateZone = False
```

Hosted Zone에 할당된 Nameserver도 확인합니다.

```bash
aws route53 get-hosted-zone \
  --id "${HOSTED_ZONE_ID}" \
  --profile otboo \
  --query "DelegationSet.NameServers" \
  --output table
```

Route 53 Domains에 등록된 Nameserver 4개와
Public Hosted Zone의 Nameserver 4개가 모두 일치하는 것을 확인했습니다.

따라서 `otboo.work`가 현재 Public Hosted Zone에
정상적으로 위임되어 있음을 확인했습니다.

AWS 계정 ID, 사용자 ARN, 인증정보 및 도메인 구매 계정의 민감정보는
Issue, PR 또는 저장소 문서에 기록하지 않습니다.

### ALB Alias A 레코드 구성

연결 대상인 기존 운영 ALB의 정보를 확인합니다.

```bash
aws elbv2 describe-load-balancers \
  --names otboo-prod-alb \
  --region ap-northeast-2 \
  --profile otboo \
  --query "LoadBalancers[0].[DNSName,CanonicalHostedZoneId,Scheme,State.Code]" \
  --output table
```

운영 ALB가 다음 상태임을 확인했습니다.

```text
Scheme     = internet-facing
State.Code = active
```

Alias 생성 전 `otboo.work` 루트 도메인에
기존 A 레코드가 없는지도 확인했습니다.

```bash
aws route53 list-resource-record-sets \
  --hosted-zone-id "${HOSTED_ZONE_ID}" \
  --profile otboo \
  --query "ResourceRecordSets[?Name=='otboo.work.']" \
  --output json
```

기본 NS 및 SOA 레코드만 존재하고 기존 A 레코드는 없었으므로,
루트 도메인을 CNAME이 아닌 Route 53 Alias A 레코드로 생성했습니다.

```bash
aws route53 change-resource-record-sets \
  --hosted-zone-id "${HOSTED_ZONE_ID}" \
  --profile otboo \
  --change-batch "{
    \"Comment\": \"Route otboo.work to otboo-prod-alb\",
    \"Changes\": [
      {
        \"Action\": \"CREATE\",
        \"ResourceRecordSet\": {
          \"Name\": \"otboo.work\",
          \"Type\": \"A\",
          \"AliasTarget\": {
            \"HostedZoneId\": \"${ALB_ZONE_ID}\",
            \"DNSName\": \"${ALB_DNS}\",
            \"EvaluateTargetHealth\": false
          }
        }
      }
    ]
  }"
```

여기서 두 Hosted Zone ID는 역할이 다릅니다.

```text
HOSTED_ZONE_ID
→ otboo.work DNS 레코드를 생성할 Route 53 Hosted Zone

ALB_ZONE_ID
→ AliasTarget이 가리키는 ALB의 CanonicalHostedZoneId
```

기존 A 레코드가 없는 것을 먼저 확인했기 때문에
최초 생성에는 기존 값을 덮어쓰지 않는 `CREATE`를 사용했습니다.

변경 요청 이후 반환된 Change ID로 반영 상태를 확인합니다.

```bash
aws route53 get-change \
  --id "${CHANGE_ID}" \
  --profile otboo
```

최종 상태가 `INSYNC`인 것을 확인했습니다.

### DNS 및 HTTP 접근 검증

Route 53 변경이 `INSYNC` 상태로 반영된 이후
로컬 기본 Resolver를 통해 운영 도메인의 A 레코드를 확인합니다.

```bash
dig otboo.work A +short
dig otboo.work A
```

기본 Resolver 조회에서 다음 결과를 확인했습니다.

```text
status: NOERROR
A 레코드 정상 반환
```

로컬 환경에 설정된 Resolver뿐 아니라
외부 Public Resolver에서도 DNS 레코드가 정상적으로 조회되는지 확인합니다.

Cloudflare Public DNS Resolver인 `1.1.1.1`을 대상으로 조회했습니다.

```bash
dig +short @1.1.1.1 otboo.work A
```

확인 결과:

```text
15.164.56.75
52.78.7.146
```

상세 DNS 응답도 확인합니다.

```bash
dig @1.1.1.1 otboo.work A
```

실제 조회에서 다음 결과를 확인했습니다.

```text
SERVER: 1.1.1.1
status: NOERROR
ANSWER: 2

otboo.work.  60  IN  A  15.164.56.75
otboo.work.  60  IN  A  52.78.7.146
```

따라서 로컬 기본 Resolver뿐 아니라 외부 Public Resolver에서도
`otboo.work`의 A 레코드가 정상적으로 조회되는 것을 확인했습니다.

위 IP 주소는 검증 시점의 ALB DNS 조회 결과이며
운영 설정에서 고정 IP로 사용하지 않습니다.

실제 DNS 연결은 Route 53 Alias A 레코드를 통해
Application Load Balancer 자체를 대상으로 유지합니다.

도메인을 통한 프론트엔드 루트 요청도 확인합니다.

```bash
curl -sS -o /dev/null \
  -w "HTTP %{http_code}\nContent-Type: %{content_type}\nRemote IP: %{remote_ip}\n" \
  http://otboo.work/
```

확인 결과:

```text
HTTP 200
Content-Type: text/html;charset=UTF-8
```

브라우저에서도 `http://otboo.work`를 통한
프론트엔드 접근이 정상적으로 동작하는 것을 확인했습니다.

Spring Boot Health Check도 동일한 도메인을 통해 검증합니다.

```bash
curl -sS -i http://otboo.work/actuator/health
```

확인 결과:

```text
HTTP/1.1 200
Server: nginx/1.30.4
{"status":"UP"}
```

`Server: nginx/1.30.4` 응답 Header와
Spring Boot Actuator의 `UP` 응답을 함께 확인해
도메인 요청이 기존 Nginx Reverse Proxy를 거쳐
Spring Boot까지 정상적으로 전달되는 것을 확인했습니다.

### Issue #157 완료 기준

다음 항목을 모두 확인해 Issue #157의 작업 범위를 완료했습니다.

- `otboo.work` 운영 도메인 등록
- Route 53 Public Hosted Zone 확인
- 등록 도메인과 Hosted Zone의 Nameserver 4개 일치
- 운영 ALB `internet-facing`, `active` 상태 확인
- `otboo.work` Alias A → `otboo-prod-alb` 구성
- Route 53 변경 상태 `INSYNC`
- 외부 DNS A 레코드 정상 조회
- `http://otboo.work/` HTTP 200 및 브라우저 접근 확인
- `http://otboo.work/actuator/health` HTTP 200 및 `UP`
- Nginx Reverse Proxy 경유 확인

### 후속 작업

다음 항목은 Issue #157에 포함하지 않고
후속 HTTPS 및 운영 보안 이슈에서 진행합니다.

- ACM 인증서 발급 및 DNS 검증
- ALB HTTPS 443 Listener 구성
- HTTP → HTTPS Redirect
- Forwarded Header 처리 재검증
- CSRF Secure Cookie 운영 적용 및 검증
- HTTPS 기반 프론트엔드 및 백엔드 접근 검증
- HTTPS 환경 WebSocket 및 SSE 외부 연결 검증