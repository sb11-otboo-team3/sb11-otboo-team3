# 👕 옷장을 부탁해

> **오늘 뭐 입지?**  
> 고민은 짧게, 코디는 취향대로.

**옷장을 부탁해**는 사용자의 옷장과 취향, 현재 날씨를 바탕으로  
코디를 추천하고 OOTD를 공유할 수 있는 개인화 의상 추천 서비스입니다.

🌐 **Service:** https://otboo.work

---

## ✨ 주요 기능

- 🌤️ 현재 날씨와 취향을 반영한 코디 추천
- 👕 보유 의상 및 의상 속성 관리
- 👤 사용자 프로필과 패션 취향 관리
- 📸 OOTD 피드 등록 및 조회
- ❤️ 좋아요 · 댓글 · 팔로우
- 💬 실시간 DM
- 🔔 실시간 알림
- 🔎 피드 검색
- ⏰ 날씨 데이터 수집 및 배치 처리

---

## 🛠 Tech Stack

### Backend

`Java 17` · `Spring Boot 3.5.16` · `Gradle`  
`Spring MVC` · `Spring Security` · `Spring Data JPA`  
`Spring Batch` · `Spring Cache`

### Data & Messaging

`PostgreSQL 16` · `Redis 7.4`  
`Apache Kafka` · `Amazon MSK` · `Amazon OpenSearch`

### Realtime

`WebSocket / STOMP` · `SSE`

### Infra & DevOps

`Docker` · `Nginx` · `GitHub Actions`  
`AWS ECS Fargate` · `Amazon ECR` · `ALB`  
`Route 53` · `ACM` · `Amazon RDS` · `Amazon S3`  
`ElastiCache` · `CloudWatch` · `SNS`

### Test

`JUnit 5` · `Mockito` · `Spring Boot Test`  
`Spring Security Test` · `Spring Batch Test` · `JaCoCo`

---

## 🏗 Architecture

```text
Developer
   │
   ▼
GitHub PR
   │
   ├─ Build / Test
   └─ JaCoCo 80% Quality Gate
   │
   ▼
GitHub Actions
   │ OIDC
   ▼
Amazon ECR
   │
   ▼
ECS Fargate
┌─────────────────────────────┐
│ Nginx Sidecar :80           │
│          ↓                  │
│ Spring Boot :8080           │
│ + Frontend Static Resources │
└─────────────────────────────┘
   │
   ├─ RDS PostgreSQL
   ├─ ElastiCache Redis
   ├─ Amazon S3
   ├─ Amazon MSK
   └─ Amazon OpenSearch


Client
  │
  ▼
Route 53
  │
  ▼
ALB HTTPS / ACM
  │
  ▼
Nginx
  │
  ▼
Spring Boot
```

GitHub Actions는 OIDC로 AWS에 인증하고,  
Git Commit SHA 기반 Docker 이미지를 ECR에 저장한 뒤 ECS Fargate에 배포합니다.

운영 트래픽은 `otboo.work` → Route 53 → ALB HTTPS → Nginx → Spring Boot 순서로 전달됩니다.

> 인프라가 어떻게 여기까지 왔는지 궁금하다면 `docs/aws`에 꽤 많이 적혀 있습니다. ☁️

---

## 🚀 Local Run

### 1. 환경변수 준비

```bash
cp .env.example .env
```

`.env.example`을 참고해 필요한 환경변수를 입력합니다.

### 2. PostgreSQL / Redis 실행

```bash
docker compose up -d
```

### 3. 애플리케이션 실행

```bash
./scripts/run-local.sh
```

### 4. 테스트

```bash
./gradlew clean test
```

### Swagger UI

```text
http://localhost:8080/swagger-ui/index.html
```

---

## 📚 Documentation

세부 구현과 운영 과정은 각 문서에서 확인할 수 있습니다.

| 영역 | 문서 |
| --- | --- |
| ☁️ AWS 운영 전반 | [docs/aws/README.md](./docs/aws/README.md) |
| 🚀 ECS / ALB / 배포 | [docs/aws/ecs-alb/README.md](./docs/aws/ecs-alb/README.md) |
| 📦 ECR | [docs/aws/ecr/README.md](./docs/aws/ecr/README.md) |
| 🗄️ RDS / S3 | [docs/aws/rds-s3/README.md](./docs/aws/rds-s3/README.md) |
| ⚡ Redis / ElastiCache | [docs/aws/elasticache/README.md](./docs/aws/elasticache/README.md) |
| 📨 Amazon MSK | [docs/aws/msk/README.md](./docs/aws/msk/README.md) |
| 🔎 OpenSearch | [docs/aws/opensearch/README.md](./docs/aws/opensearch/README.md) |
| 📨 Kafka | [docs/kafka/README.md](./docs/kafka/README.md) |
| 💾 Storage | [docs/storage/README.md](./docs/storage/README.md) |
| 🧩 ERD | [docs/erd/README.md](./docs/erd/README.md) |

---

## 🔧 Engineering Highlights

- GitHub Actions **OIDC 기반 AWS 인증 및 자동 배포**
- Git Commit SHA 기반 **Immutable Docker Image**
- ECS Fargate **Rolling Update**
- ALB Health Check 기반 트래픽 전환
- Deployment Circuit Breaker 및 Rollback
- RDS / Redis Private Network 구성
- Redis TLS / AUTH 적용
- Kafka / Amazon MSK 기반 메시징
- Amazon OpenSearch 기반 검색
- WebSocket / SSE 기반 실시간 통신
- CloudWatch / SNS 기반 운영 모니터링
- JaCoCo **Line Coverage 80% Quality Gate**

---

## 👥 Team

| 이름 | 담당 |
| --- | --- |
| 강우진 | 팀장 · 공통 개발 환경 · 인프라 |
| 김지혜 | 인증 · 인가 · 프로필 |
| 김관희 | 의상 관리 |
| 최정윤 | 날씨 · 배치 · 성능 |
| 정수용 | 댓글 · 좋아요 · 알림 · 소셜 기능 |

---

## 👋 마지막으로

옷은 많은데 입을 옷이 없을 때,

### **옷장을 부탁해 👕**