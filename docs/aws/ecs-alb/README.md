# ECS Fargate 및 ALB 최초 수동 배포

## 1. 문서 목적

이 문서는 `옷장을 부탁해` 애플리케이션을 AWS ECS Fargate에서 실행하고,
Application Load Balancer를 통해 외부에서 접근할 수 있도록 구성한
최초 수동 배포 환경과 검증 절차를 정리합니다.

React·Vite 프론트엔드 정적 리소스는 Spring Boot 애플리케이션에 포함하며,
ALB DNS의 루트 `/` 경로에서 프론트엔드와 백엔드 API를 함께 제공합니다.

이번 배포에서는 최초 배포의 정상 동작 확인을 우선합니다.

다음 항목은 후속 이슈에서 진행합니다.

- GitHub Actions OIDC 기반 자동 배포
- Nginx Reverse Proxy
- 도메인 및 HTTPS
- ECS 다중 Task
- Rolling Update 및 무중단 배포
- 장시간 WebSocket·SSE 연결 및 재연결
- AWS 예상 비용 산정 및 비용 최적화

실제 AWS 계정 ID, Endpoint, Secret 값, 사용자 ARN은
Issue, PR, README에 기록하지 않습니다.

---

## 2. 관련 이슈 및 문서

- Issue #22: ECS Cluster, Task Definition, Service 및 ALB 구성
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

### 후속 검증

다음 항목은 팀원 도메인 구현 또는 후속 인프라 이슈에서 진행합니다.

- 프론트엔드와 백엔드 전체 API 명세 비교
- Redis 사용 기능의 실제 저장·조회
- 프로필 및 의상 이미지 업로드·조회·삭제
- WebSocket 실제 메시지 송수신
- SSE 실제 이벤트 수신
- Spring Security 기본 사용자 자동 생성 로그 제거
- 장시간 WebSocket·SSE 연결 및 재연결
- ECS 다중 Task
- Rolling Update
- 무중단 배포
- GitHub Actions OIDC 자동 배포
- 도메인 및 HTTPS
