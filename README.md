# 옷장을 부탁해

날씨와 사용자의 취향을 고려하여 보유한 의상 조합을 추천하고,  
OOTD 피드·팔로우·댓글·좋아요·DM 등의 소셜 기능을 제공하는 서비스입니다.

---

## 프로젝트 소개

사용자가 보유한 의상을 등록하면 현재 날씨와 개인 취향을 기반으로 적절한 코디를 추천합니다.

추천 결과를 OOTD 피드에 공유하고 다른 사용자와 소통할 수 있으며,  
팔로우, 댓글, 좋아요, 알림, DM 기능을 제공합니다.

---

## 주요 기능

- 회원가입 및 로그인
- 사용자 프로필과 취향 관리
- 보유 의상 등록 및 관리
- 날씨 기반 코디 추천
- OOTD 피드 등록 및 조회
- 댓글, 좋아요, 팔로우
- 실시간 알림 및 DM
- 날씨 데이터 수집 및 배치 처리

---

## 예시 화면

> 아래 이미지는 서비스 기능과 화면 구성을 설명하기 위한 프로토타입 예시입니다.  
> 실제 구현 결과는 개발 과정에서 변경될 수 있습니다.

<!-- 이미지가 준비되면 아래 주석을 제거하세요. -->

<!--
### 의상 추천

![의상 추천 화면](docs/images/outfit-recommendation.png)

### OOTD 피드

![OOTD 피드 화면](docs/images/ootd-feed.png)

### DM

![DM 화면](docs/images/direct-message.png)
-->

---

## 기술 스택

### Backend

- Java 17
- Spring Boot 4.0.7
- Spring MVC
- Spring Security
- Spring Data JPA
- Spring Batch
- WebSocket
- Server-Sent Events
- Gradle

### Database and Cache

- PostgreSQL 16
- Redis 7.4
- H2

### Infrastructure

- Docker
- Docker Compose
- GitHub Actions
- AWS ECS
- AWS ECR
- Nginx

> AWS 배포 환경은 프로젝트 진행 과정에서 구성할 예정입니다.

---

## 로컬 개발 환경

### 사전 준비

다음 프로그램이 설치되어 있어야 합니다.

- Java 17
- Docker Desktop
- Git

버전을 확인합니다.

```bash
java -version
docker --version
docker compose version
git --version
```

Java는 17 버전을 사용해야 합니다.

---

## 프로젝트 실행 방법

### 1. 저장소 복제

```bash
git clone <저장소 주소>
cd sb11-otboo-team3
```

### 2. 환경변수 파일 생성

```bash
cp .env.example .env
```

기본 로컬 환경에서는 `.env.example`의 값을 그대로 사용할 수 있습니다.

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=otboo
DB_USERNAME=otboo
DB_PASSWORD=otboo

REDIS_HOST=localhost
REDIS_PORT=6379
```

`.env`에는 로컬 환경 정보와 민감 정보가 들어갈 수 있으므로 Git에 커밋하지 않습니다.

### 3. PostgreSQL과 Redis 실행

```bash
docker compose up -d
```

컨테이너 상태를 확인합니다.

```bash
docker compose ps
```

다음 두 컨테이너가 `healthy` 상태여야 합니다.

```text
otboo-local-postgres-1
otboo-local-redis-1
```

### 4. 환경변수 적용

```bash
set -a
source .env
set +a
```

### 5. 애플리케이션 실행

```bash
./gradlew bootRun
```

정상 실행 시 기본 주소는 다음과 같습니다.

```text
http://localhost:8080
```

서버를 종료할 때는 실행 중인 터미널에서 `Control + C`를 누릅니다.

---

## 테스트

전체 테스트를 실행합니다.

```bash
./gradlew clean test
```

테스트는 `test` 프로필과 H2 인메모리 데이터베이스를 사용하므로  
로컬 PostgreSQL과 Redis 실행 여부에 의존하지 않습니다.

---

## Docker 관리 명령

### 컨테이너 상태 확인

```bash
docker compose ps
```

### 컨테이너 중지

```bash
docker compose stop
```

### 중지한 컨테이너 다시 실행

```bash
docker compose start
```

### 컨테이너와 네트워크 제거

```bash
docker compose down
```

PostgreSQL 데이터 볼륨은 유지됩니다.

> `docker compose down -v`는 PostgreSQL 데이터까지 삭제하므로  
> 데이터 초기화가 필요한 경우에만 사용합니다.

---

## 패키지 구조

도메인별 책임을 명확히 구분하기 위해 도메인형 패키지 구조를 사용합니다.

```text
com.otboo
├── global
│   ├── config
│   ├── error
│   ├── security
│   ├── common
│   ├── logging
│   └── infrastructure
│
└── domain
    ├── auth
    ├── user
    ├── profile
    ├── clothes
    ├── weather
    ├── recommendation
    ├── feed
    ├── comment
    ├── follow
    ├── notification
    └── directmessage
```

각 도메인 내부 구조는 기능 구현 과정에서 생성합니다.

```text
domain/{도메인명}
├── controller
├── service
├── repository
├── entity
├── dto
├── mapper
└── exception
```

---

## Git 규칙

### 브랜치

```text
main
develop
feature/{이슈번호}-{기능요약}
bugfix/{이슈번호}-{기능요약}
hotfix/{이슈번호}-{기능요약}
```

`main`과 `develop` 브랜치에는 직접 Push하지 않습니다.

### 커밋 메시지

```text
Tag: 작업 내용 [#이슈번호]
```

예시:

```text
Feat: 의상 등록 API 구현 [#12]
Fix: 날씨 조회 중복 반환 오류 수정 [#25]
Chore: PostgreSQL 및 Redis 로컬 환경 구성 [#1]
```

---

## 팀원

| 이름 | 담당 |
| --- | --- |
| 강우진 | 팀장, 공통 개발 환경 및 인프라 |
| 김지혜 | 인증·인가 및 프로필 |
| 관희 | 의상 관리 |
| 정윤 | 날씨, 배치 및 성능 |
| 수용 | 댓글, 좋아요, 알림 및 소셜 기능 |

> 담당 범위는 프로젝트 진행 상황에 따라 조정될 수 있습니다.