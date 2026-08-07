# 옷장을 부탁해

날씨와 사용자의 취향을 바탕으로 보유 의상 조합을 추천하고, OOTD 피드·팔로우·댓글·좋아요·알림·DM 기능을 제공하는 서비스입니다.

---

## 프로젝트 소개

사용자가 보유한 의상을 등록하면 현재 날씨와 개인 취향을 기반으로 적절한 코디를 추천합니다.

추천 결과를 OOTD 피드에 공유하고 다른 사용자와 소통할 수 있으며, 팔로우, 댓글, 좋아요, 알림, DM 기능을 제공합니다.

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

## 기술 스택

### Backend

- Java 17
- Spring Boot 3.5.16
- Gradle Wrapper 8.14.3
- Spring MVC
- Spring Security
- Spring Data JPA
- Spring Batch
- Spring Cache
- WebSocket
- springdoc-openapi

### Database and Cache

- PostgreSQL 16
- Redis 7.4
- H2

### Test

- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test
- Spring Batch Test
- JaCoCo

### Development and Collaboration

- Docker
- Docker Compose
- GitHub
- GitHub Actions
- IntelliJ IDEA

---

## 로컬 실행 준비

다음 프로그램이 설치되어 있어야 합니다.

- Java 17
- Git
- Docker Desktop
- IntelliJ IDEA

설치 상태와 버전을 확인합니다.

```bash
java -version
git --version
docker --version
docker compose version
```

Java 버전은 17이어야 합니다.

Gradle은 별도로 설치하지 않고 프로젝트에 포함된 Gradle Wrapper를 사용합니다.

```bash
./gradlew --version
```

Windows 환경에서 Git Bash를 사용하는 경우 다음 문서를 확인합니다.

[Windows Git Bash 실행 가이드](./README_WINDOWS.md)

---

## 로컬 실행 방법

전체 실행 순서는 다음과 같습니다.

```text
저장소 Clone
→ .env 파일 생성
→ Docker Desktop 실행
→ PostgreSQL·Redis 실행
→ 애플리케이션 실행
→ Swagger 확인
→ 테스트 실행
```

### 1. 저장소 복제

GitHub 저장소 페이지에서 `Code` 버튼을 누른 뒤 HTTPS 주소를 복사합니다.

```bash
git clone 복사한-HTTPS-주소
cd sb11-otboo-team3
```

이미 저장소를 복제했다면 최신 `develop` 브랜치를 받습니다.

```bash
git switch develop
git pull origin develop
```

현재 브랜치를 확인합니다.

```bash
git branch --show-current
```

정상 결과:

```text
develop
```

---

### 2. 환경변수 파일 생성

프로젝트 루트에서 `.env.example`을 복사하여 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

파일 생성 여부를 확인합니다.

```bash
ls -a
```

다음 파일이 모두 보여야 합니다.

```text
.env
.env.example
```

`.env`에는 `.env.example`에 정의된 환경변수 값을 입력합니다.

기본 로컬 인프라 설정 예시는 다음과 같습니다.

```dotenv
# PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_NAME=otboo
DB_USERNAME=otboo
DB_PASSWORD=otboo

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
```

S3 연동 기능을 로컬에서 테스트할 때는 본인의 AWS CLI 프로필과 프로젝트 버킷 정보를 입력합니다.

```dotenv
AWS_REGION=ap-northeast-2
AWS_PROFILE=본인의_AWS_CLI_프로필명
S3_BUCKET=otboo-prod-assets-310567229825-ap-northeast-2
```

JWT Secret과 외부 API Key 등 추가 값도 반드시 `.env.example`에 정의된 변수명을 그대로 사용합니다.

공백이나 셸에서 해석될 수 있는 특수문자가 포함된 값은 큰따옴표로 감쌉니다.

```dotenv
JWT_SECRET="특수문자가-포함된-긴-값"
```

`.env`에는 로컬 환경 정보와 민감정보가 포함될 수 있으므로 Git에 커밋하지 않습니다.

다음 명령으로 `.env`가 Git 변경 파일에 표시되지 않는지 확인합니다.

```bash
git status --short
```

> Spring Boot의 `./gradlew bootRun`은 프로젝트 루트의 `.env`를 자동으로 읽지 않습니다.  
> 이 프로젝트에서는 아래의 `./scripts/run-local.sh`를 팀 공통 로컬 실행 명령으로 사용합니다.

---

### 3. PostgreSQL과 Redis 실행

Docker Desktop을 먼저 실행합니다.

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
docker compose up -d
```

컨테이너 상태를 확인합니다.

```bash
docker compose ps
```

`postgres`와 `redis`가 모두 `healthy` 상태가 될 때까지 기다립니다.

예상 서비스:

```text
postgres
redis
```

로그 확인이 필요한 경우 다음 명령을 사용합니다.

PostgreSQL 로그:

```bash
docker compose logs postgres
```

Redis 로그:

```bash
docker compose logs redis
```

실시간 로그 확인:

```bash
docker compose logs -f postgres
```

실시간 로그 확인을 종료할 때는 다음 키를 누릅니다.

```text
Control + C
```

---

### 4. Spring Boot 환경변수 로드 방식

Docker Compose와 Spring Boot의 `.env` 처리 방식은 서로 다릅니다.

- Docker Compose는 Compose 설정에서 참조한 `.env` 값을 컨테이너 환경변수로 전달합니다.
- Spring Boot의 `./gradlew bootRun`은 프로젝트 루트의 `.env`를 자동으로 읽지 않습니다.
- 팀 공통 로컬 실행은 `./scripts/run-local.sh`를 사용합니다.

`./scripts/run-local.sh`는 다음 순서로 동작합니다.

```text
프로젝트 루트의 .env 존재 확인
→ .env 값을 현재 프로세스 환경변수로 등록
→ local 프로필로 Spring Boot 실행
```

따라서 `.env`의 DB, Redis, S3, JWT 설정을 반영하려면 `./gradlew bootRun`을 직접 실행하지 말고 다음 스크립트를 사용합니다.

```bash
./scripts/run-local.sh
```

스크립트는 별도의 Gradle 의존성이나 IntelliJ 플러그인 없이 동작합니다.

> `.env` 파일은 Bash 문법으로 로드됩니다.  
> 값에 공백 또는 특수문자가 포함되면 큰따옴표로 감싸고, 키와 값 사이에 공백을 넣지 않습니다.

올바른 예:

```dotenv
JWT_SECRET="my local secret"
```

잘못된 예:

```dotenv
JWT_SECRET=my local secret
```

---

### 5. 애플리케이션 실행

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
./scripts/run-local.sh
```

실행 권한 오류가 발생하면 다음 명령을 실행합니다.

```bash
chmod +x gradlew
chmod +x scripts/run-local.sh
```

그다음 다시 실행합니다.

```bash
./scripts/run-local.sh
```

스크립트에서 기본 활성 프로필은 `local`입니다.

정상 실행 주소:

```text
http://localhost:8080
```

실행 로그에 다음과 비슷한 문구가 나타나면 정상입니다.

```text
The following 1 profile is active: "local"
Tomcat started on port 8080
Started OtbooApplication
```

서버를 종료할 때는 실행 중인 터미널에서 다음 키를 누릅니다.

```text
Control + C
```

---

## IntelliJ에서 실행

터미널 대신 IntelliJ에서 애플리케이션을 실행할 수 있습니다.

1. IntelliJ를 실행합니다.
2. `Open`을 선택합니다.
3. `sb11-otboo-team3` 프로젝트 폴더를 선택합니다.
4. Gradle 프로젝트 동기화가 끝날 때까지 기다립니다.
5. Project SDK가 Java 17인지 확인합니다.
6. Gradle JVM이 Java 17인지 확인합니다.
7. Spring Boot Application 클래스를 엽니다.
8. 클래스 왼쪽의 실행 버튼을 누릅니다.

### Project SDK 확인

```text
File
→ Project Structure
→ Project
→ SDK
→ Java 17
```

### Gradle JVM 확인

macOS:

```text
IntelliJ IDEA
→ Settings
→ Build, Execution, Deployment
→ Build Tools
→ Gradle
→ Gradle JVM
→ Java 17
```

### IntelliJ 환경변수 설정

IntelliJ에서 Spring Boot Application을 직접 실행하면 프로젝트 루트의 `.env`가 자동으로 적용되지 않습니다.

직접 실행할 때는 다음 위치에 `.env`와 동일한 환경변수를 등록합니다.

```text
Run
→ Edit Configurations
→ Spring Boot 실행 설정
→ Environment variables
```

예시:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=otboo
DB_USERNAME=otboo
DB_PASSWORD=otboo
REDIS_HOST=localhost
REDIS_PORT=6379
AWS_REGION=ap-northeast-2
AWS_PROFILE=본인의_AWS_CLI_프로필명
S3_BUCKET=otboo-prod-assets-310567229825-ap-northeast-2
```

EnvFile 플러그인을 개인적으로 사용할 수도 있지만 필수 도구는 아닙니다.

팀 공통 실행 기준은 별도 플러그인이 필요 없는 다음 명령입니다.

```bash
./scripts/run-local.sh
```

---

## API 문서 확인

애플리케이션 실행 후 브라우저에서 다음 주소에 접속합니다.

### Swagger UI

```text
http://localhost:8080/swagger-ui/index.html
```

### OpenAPI JSON

```text
http://localhost:8080/v3/api-docs
```

현재 Spring Security 기본 인증 화면이 나타나면 다음 정보를 사용합니다.

```text
사용자 이름: user
비밀번호: 애플리케이션 실행 로그의 generated security password
```

실행 로그에서 다음 문구를 찾습니다.

```text
Using generated security password:
```

애플리케이션을 다시 실행하면 임시 비밀번호가 변경될 수 있습니다.

인증·인가 기능이 적용되면 Swagger 접근 방식은 변경될 수 있습니다.

OpenAPI 문서에는 다음 기본 정보가 표시됩니다.

```text
제목: 옷장을 부탁해 API
설명: 개인화 의상 추천 서비스 API 문서
버전: v1
```

---

## 테스트

### 전체 테스트 실행

```bash
./gradlew clean test
```

정상 결과:

```text
BUILD SUCCESSFUL
```

### CI와 동일한 전체 빌드

```bash
./gradlew clean build
```

GitHub Actions에서도 다음 명령으로 전체 빌드와 테스트를 실행합니다.

```text
./gradlew clean build
```

테스트가 하나라도 실패하면 CI가 실패하며 `develop` 브랜치에 Merge할 수 없습니다.

새 테스트는 가능한 한 로컬 PostgreSQL이나 Redis 같은 외부 환경에 의존하지 않도록 작성합니다.

### 특정 테스트 클래스 실행

```bash
./gradlew test --tests "패키지명.테스트클래스명"
```

예시:

```bash
./gradlew test --tests "com.otboo.global.config.OpenApiConfigTest"
```

### 특정 테스트 메서드 실행

```bash
./gradlew test --tests "패키지명.테스트클래스명.테스트메서드명"
```

---

## JaCoCo 테스트 커버리지

테스트가 끝나면 JaCoCo 커버리지 보고서가 생성됩니다.

```bash
./gradlew clean test
```

HTML 보고서 경로:

```text
build/reports/jacoco/test/html/index.html
```

macOS에서 보고서를 열려면 다음 명령을 실행합니다.

```bash
open build/reports/jacoco/test/html/index.html
```

현재 커버리지는 측정만 수행합니다.

커버리지 80% 미달을 CI 실패 또는 Merge 차단 조건으로 사용하지 않습니다.

```text
테스트 실패
→ CI 실패
→ Merge 불가

테스트 성공
→ 커버리지와 관계없이 CI 성공
→ 승인과 리뷰 조건 충족 후 Merge 가능
```

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

### 중지한 컨테이너 다시 시작

```bash
docker compose start
```

### 컨테이너 재시작

```bash
docker compose restart
```

### 컨테이너와 네트워크 제거

```bash
docker compose down
```

`docker compose down`을 실행해도 PostgreSQL 데이터 볼륨은 유지됩니다.

### PostgreSQL 데이터까지 완전히 삭제

```bash
docker compose down -v
```

`docker compose down -v`는 로컬 PostgreSQL 데이터까지 모두 삭제합니다.

데이터 초기화가 필요한 경우에만 사용합니다.

---

## 자주 발생하는 문제

### Docker 명령이 실행되지 않음

Docker Desktop이 실행 중인지 확인합니다.

```bash
docker info
```

Docker 서버 연결 오류가 발생하면 Docker Desktop을 실행하거나 재시작합니다.

---

### PostgreSQL 5432 포트 충돌

5432 포트를 사용 중인 프로세스를 확인합니다.

```bash
lsof -nP -iTCP:5432 -sTCP:LISTEN
```

macOS에서 Homebrew PostgreSQL이 실행 중인지 확인합니다.

```bash
brew services list
```

기존 PostgreSQL을 중지하거나 `.env`의 `DB_PORT`를 변경합니다.

```dotenv
DB_PORT=5433
```

Docker Compose를 다시 실행합니다.

```bash
docker compose down
docker compose up -d
```

.env의 `DB_PORT` 값을 변경한 뒤 로컬 실행 스크립트를 사용합니다.

```dotenv
DB_PORT=5433
```

```bash
./scripts/run-local.sh
```

---

### Redis 6379 포트 충돌

6379 포트를 사용 중인 프로세스를 확인합니다.

```bash
lsof -nP -iTCP:6379 -sTCP:LISTEN
```

기존 Redis를 중지하거나 `.env`의 `REDIS_PORT`를 변경합니다.

```dotenv
REDIS_PORT=6380
```

Docker Compose를 다시 실행합니다.

```bash
docker compose down
docker compose up -d
```

.env의 `REDIS_PORT` 값을 변경한 뒤 로컬 실행 스크립트를 사용합니다.

```dotenv
REDIS_PORT=6380
```

```bash
./scripts/run-local.sh
```

---

### 애플리케이션 8080 포트 충돌

8080 포트를 사용 중인 프로세스를 확인합니다.

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

8080 포트를 사용 중인 기존 애플리케이션을 종료한 뒤 다시 실행합니다.

---

### PostgreSQL 인증 오류

`.env`의 계정 정보를 변경했는데 기존 PostgreSQL 볼륨에 이전 계정 정보가 남아 있으면 인증 오류가 발생할 수 있습니다.

로컬 데이터를 삭제해도 되는 경우에만 다음 명령을 실행합니다.

```bash
docker compose down -v
docker compose up -d
```

.env의 계정 정보를 변경한 뒤 로컬 실행 스크립트를 사용합니다.

```dotenv
DB_USERNAME=변경한-사용자명
DB_PASSWORD="변경한-비밀번호"
```

```bash
./scripts/run-local.sh
```

---

### 컨테이너가 healthy 상태가 되지 않음

컨테이너 상태를 확인합니다.

```bash
docker compose ps
```

PostgreSQL 로그:

```bash
docker compose logs postgres
```

Redis 로그:

```bash
docker compose logs redis
```

---

### Gradle 실행 권한 오류

다음과 같은 오류가 발생할 수 있습니다.

```text
Permission denied: ./gradlew
```

실행 권한을 부여합니다.

```bash
chmod +x gradlew
```

---

### Java 버전 오류

Java 버전을 확인합니다.

```bash
java -version
```

Java 17이 아니라면 IntelliJ의 Project SDK와 Gradle JVM을 모두 Java 17로 변경합니다.

---

### Swagger 접속 시 인증 화면이 표시됨

현재 Spring Security 기본 설정으로 인해 인증이 필요할 수 있습니다.

```text
사용자 이름: user
비밀번호: 실행 로그의 generated security password
```

실행 로그에서 다음 문구를 확인합니다.

```text
Using generated security password:
```

---

## Git 작업 규칙

### 작업 순서

```text
Issue 생성
→ develop 최신화
→ 작업 브랜치 생성
→ 구현 및 테스트
→ Commit
→ Push
→ Pull Request
→ Review
→ Merge
```

### 작업 브랜치 생성

항상 최신 `develop` 브랜치에서 작업 브랜치를 생성합니다.

```bash
git switch develop
git pull origin develop
git switch -c feature/이슈번호-기능요약
```

예시:

```bash
git switch -c feature/13-local-readme
```

### 브랜치 이름

```text
main
develop
feature/{이슈번호}-{기능요약}
bugfix/{이슈번호}-{기능요약}
hotfix/{이슈번호}-{기능요약}
```

예시:

```text
feature/13-local-readme
bugfix/25-weather-duplicate
```

`main`과 `develop` 브랜치에는 직접 Push하지 않습니다.

---

### 커밋 메시지

```text
Tag: 작업 내용 [#이슈번호]
```

Tag 예시:

```text
Feat
Fix
Refactor
Docs
Test
Chore
Infra
Perf
```

커밋 예시:

```text
Feat: 의상 등록 API 구현 [#12]
Fix: 날씨 조회 중복 반환 오류 수정 [#25]
Docs: 공통 로컬 실행 방법 정리 [#13]
```

---

### 변경사항 확인 및 Push

현재 변경 상태를 확인합니다.

```bash
git status
```

변경 파일을 추가합니다.

```bash
git add 변경한-파일
```

커밋합니다.

```bash
git commit -m "Tag: 작업 내용 [#이슈번호]"
```

현재 브랜치를 원격 저장소에 Push합니다.

```bash
git push -u origin 현재-브랜치명
```

현재 브랜치 이름을 확인합니다.

```bash
git branch --show-current
```

---

## Pull Request 및 Merge 규칙

`develop` 브랜치는 GitHub Ruleset으로 보호합니다.

Merge하려면 다음 조건을 모두 충족해야 합니다.

- Pull Request를 통한 변경
- 팀원 승인 2개
- 최신 Push에 대한 승인
- 리뷰 Conversation 해결
- `Build and Test` 상태 검사 통과

테스트가 실패하면 Merge할 수 없습니다.

```text
테스트 성공
→ CI 성공
→ 승인과 리뷰 조건 충족
→ Merge 가능

테스트 실패
→ CI 실패
→ Merge 차단
```

`develop` 브랜치의 Force Push와 삭제는 제한합니다.

---

## GitHub Actions CI

GitHub Actions는 다음 상황에서 실행됩니다.

- `develop` 또는 `main`을 대상으로 Pull Request 생성
- `develop` 또는 `main`에 Push

CI 실행 과정:

```text
소스 코드 Checkout
→ JDK 17 설정
→ Gradle 설정
→ ./gradlew clean build
→ JaCoCo 커버리지 보고서 업로드
```

테스트 실패는 CI 실패로 처리됩니다.

커버리지 보고서 업로드 실패는 CI 성공 여부에 영향을 주지 않습니다.

---

## 공통 코드 변경 규칙

다음 항목은 다른 팀원의 작업에 영향을 줄 수 있으므로 변경 전에 팀에 공유합니다.

- 공통 Entity
- 공통 DTO
- 공통 예외 응답
- Security 설정
- 환경변수
- Docker Compose
- Gradle 의존성
- 공통 API 경로
- ERD 테이블 및 연관관계
- GitHub Actions Workflow

공통 코드 변경도 Issue, Branch, Pull Request 절차를 따릅니다.

---

## 운영 Docker 이미지 빌드 및 로컬 검증

Spring Boot 애플리케이션은 멀티 스테이지 `Dockerfile`을 사용하여
빌드 환경과 실행 환경을 분리합니다.

빌드 단계에서는 Java 17 JDK와 Gradle Wrapper로 실행 가능한
Spring Boot JAR를 생성합니다.

최종 실행 이미지에는 Java 17 런타임과 애플리케이션 JAR만 포함하며,
비루트 사용자 `otboo`로 애플리케이션을 실행합니다.

실제 비밀번호와 인증정보는 Docker 이미지에 포함하지 않고
컨테이너 실행 시 환경변수로 주입합니다.

---

### 사전 준비

Docker Desktop이 실행 중인지 확인한 후
PostgreSQL과 Redis를 실행합니다.

```bash
docker compose up -d
```

실행 상태를 확인합니다.

```bash
docker compose ps
```

PostgreSQL과 Redis가 모두 `healthy` 상태여야 합니다.

```bash
docker compose exec -T postgres pg_isready
docker compose exec -T redis redis-cli ping
```

정상 응답 예시:

```text
/var/run/postgresql:5432 - accepting connections
PONG
```

---

### 기본 아키텍처 이미지 빌드

현재 Docker 실행 환경의 기본 아키텍처로 이미지를 빌드합니다.

```bash
docker build \
  --progress=plain \
  -t otboo:local .
```

생성된 이미지 정보를 확인합니다.

```bash
docker image inspect otboo:local \
  --format 'os={{.Os}} architecture={{.Architecture}} user={{.Config.User}}'
```

Apple Silicon Mac에서 별도 플랫폼을 지정하지 않고 빌드하면
일반적으로 `linux/arm64` 이미지가 생성됩니다.

---

### AMD64 이미지 빌드

향후 ECS Task Definition에서 `X86_64` 아키텍처를 사용할 경우
`linux/amd64` 이미지를 빌드합니다.

```bash
docker build \
  --platform linux/amd64 \
  --progress=plain \
  -t otboo:amd64 .
```

이미지 아키텍처를 확인합니다.

```bash
docker image inspect otboo:amd64 \
  --format 'os={{.Os}} architecture={{.Architecture}} user={{.Config.User}}'
```

정상 결과:

```text
os=linux architecture=amd64 user=otboo
```

---

### 실행 이미지 내부 확인

Java 버전을 확인합니다.

```bash
docker run --rm \
  --entrypoint java \
  otboo:local \
  -version
```

최종 실행 이미지에 JDK 컴파일러가 포함되지 않았는지 확인합니다.

```bash
docker run --rm \
  --entrypoint sh \
  otboo:local \
  -c 'command -v javac || echo "javac 없음: 정상"'
```

비루트 사용자와 애플리케이션 작업 디렉터리의 파일을 확인합니다.

```bash
docker run --rm \
  --entrypoint sh \
  otboo:local \
  -c 'id && ls -lah /app'
```

정상 기준:

```text
Java 17 런타임 사용
javac 없음
uid=10001(otboo)
애플리케이션 작업 디렉터리 /app에 app.jar만 존재
```

---

### 로컬 실행 환경변수 준비

PostgreSQL 컨테이너의 접속 정보를 현재 터미널의
셸 변수로 저장합니다.

```bash
DB_USERNAME="$(docker compose exec -T postgres printenv POSTGRES_USER)"
DB_PASSWORD="$(docker compose exec -T postgres printenv POSTGRES_PASSWORD)"
DB_NAME="$(docker compose exec -T postgres printenv POSTGRES_DB)"
```

값 자체를 출력하지 않고 설정 여부만 확인합니다.

```bash
test -n "$DB_USERNAME" && echo "DB_USERNAME 확인 완료"
test -n "$DB_PASSWORD" && echo "DB_PASSWORD 확인 완료"
test -n "$DB_NAME" && echo "DB_NAME 확인 완료"
```

PostgreSQL과 Redis가 참여한 Docker 네트워크 이름을 가져옵니다.

```bash
POSTGRES_CONTAINER="$(docker compose ps -q postgres)"

NETWORK_NAME="$(docker inspect "$POSTGRES_CONTAINER" \
  --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}')"
```

네트워크 이름을 확인합니다.

```bash
echo "Docker 네트워크: $NETWORK_NAME"
```

로컬 검증에 사용할 임시 Base64 JWT Secret을 생성합니다.

```bash
JWT_SECRET_VALUE="$(openssl rand -base64 64 | tr -d '\n')"
```

Secret 값은 출력하거나 Git 저장소에 기록하지 않습니다.

생성 여부와 디코딩되는 길이만 확인합니다.

```bash
test -n "$JWT_SECRET_VALUE" \
  && echo "JWT_SECRET 생성 완료: ${#JWT_SECRET_VALUE}자"

printf '%s' "$JWT_SECRET_VALUE" \
  | openssl base64 -d -A \
  | wc -c
```

Base64 디코딩 결과가 `64`바이트이면 정상입니다.

운영 환경변수 목록은 `.env.prod.example`을 참고합니다.

---

### 운영 프로필로 컨테이너 실행

기존 애플리케이션 컨테이너가 있다면 제거합니다.

```bash
docker rm -f otboo-app 2>/dev/null || true
```

PostgreSQL과 Redis가 실행되는 Docker 네트워크에
애플리케이션 컨테이너를 연결합니다.

```bash
docker run -d \
  --name otboo-app \
  --network "$NETWORK_NAME" \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SERVER_PORT=8080 \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME="$DB_NAME" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD= \
  -e JWT_SECRET="$JWT_SECRET_VALUE" \
  otboo:local
```

같은 Docker 네트워크에 있는 컨테이너끼리는
`localhost` 대신 Compose 서비스명인 `postgres`, `redis`를 사용합니다.

컨테이너 실행 상태를 확인합니다.

```bash
docker ps --filter name=otboo-app
```

애플리케이션 로그를 확인합니다.

```bash
docker logs otboo-app --tail 150
```

정상 실행 기준:

```text
prod 프로필 활성화
PostgreSQL 연결 성공
Tomcat 8080 포트 실행
Started OtbooApplication
```

---

### Health Check 확인

Actuator Health Check를 호출합니다.

```bash
curl -i http://localhost:8080/actuator/health
```

정상 응답:

```text
HTTP/1.1 200
```

```json
{"status":"UP"}
```

운영 환경에서는 Actuator 웹 노출 대상을 `health`로 제한합니다.

ALB Health Check 경로도 다음 주소를 사용합니다.

```text
/actuator/health
```

사용자 정의 `SecurityFilterChain`이 추가되거나 변경되면
`/actuator/health`의 비인증 접근 여부를 다시 확인합니다.

---

### 비루트 사용자 실행 확인

실행 중인 애플리케이션 컨테이너의 사용자를 확인합니다.

```bash
docker exec otboo-app id
```

정상 결과:

```text
uid=10001(otboo) gid=10001(otboo)
```

---

### Graceful Shutdown 확인

Spring Boot 운영 프로필의 Graceful Shutdown 제한 시간은 30초입니다.

Docker가 애플리케이션 종료를 최대 35초까지 기다리도록 설정하여
컨테이너를 종료합니다.

```bash
docker stop -t 35 otboo-app
```

종료 상태를 확인합니다.

```bash
docker inspect otboo-app \
  --format 'status={{.State.Status}} exitCode={{.State.ExitCode}}'
```

`docker stop`으로 SIGTERM을 전달한 경우
종료 코드가 `143`으로 표시될 수 있습니다.

종료 로그를 확인합니다.

```bash
docker logs otboo-app --tail 100
```

정상 종료 기준:

```text
Commencing graceful shutdown
Graceful shutdown complete
Closing JPA EntityManagerFactory
HikariPool-1 - Shutdown completed
```

검증이 끝난 애플리케이션 컨테이너를 제거합니다.

```bash
docker rm otboo-app
```

PostgreSQL과 Redis 컨테이너는 그대로 유지할 수 있습니다.

로컬 데이터 볼륨을 보존해야 할 때는 다음 명령을 실행하지 않습니다.

```bash
docker compose down -v
```

---

### 이미지와 ECS 아키텍처 일치

Dockerfile의 `FROM` 구문에는 특정 플랫폼을 고정하지 않습니다.

이미지를 배포할 때는 Docker 이미지 아키텍처와
ECS Task Definition의 CPU 아키텍처를 일치시킵니다.

| Docker 이미지 플랫폼 | ECS CPU 아키텍처 |
| --- | --- |
| `linux/arm64` | `ARM64` |
| `linux/amd64` | `X86_64` |

현재 로컬 검증에서는 다음 이미지를 생성했습니다.

```text
otboo:local  → 호스트 기본 아키텍처
otboo:amd64  → linux/amd64
```

ECS에서 사용할 최종 CPU 아키텍처는
ECS Task Definition 구성 단계에서 결정합니다.

---

### 운영 환경의 비밀값 관리

다음 값은 Dockerfile, Docker 이미지 및 Git 저장소에 포함하지 않습니다.

```text
DB_PASSWORD
REDIS_PASSWORD
JWT_SECRET
AWS 인증정보
외부 API 인증정보
```

실제 운영 환경에서는 ECS Task Definition을 통해 환경변수를 주입하고,
민감정보는 AWS Secrets Manager 또는 Parameter Store에서 관리합니다.

`.env`, `.env.prod` 등의 실제 환경변수 파일은
Docker 이미지에 복사하지 않습니다.

저장소에는 값이 비어 있는 `.env.prod.example`만 포함합니다.

---

### Docker 빌드와 테스트의 분리

Docker 이미지 생성 단계에서는 실행 가능한 JAR를 만들기 위해
다음 Gradle 작업을 수행합니다.

```text
./gradlew clean bootJar --no-daemon -x test
```

Docker 이미지 빌드와 별도로 전체 테스트 및 빌드는 반드시 실행합니다.

```bash
./gradlew clean build
```

전체 테스트가 실패하면 이미지를 배포하거나 Pull Request를 Merge하지 않습니다.

## 팀원과 담당

| 이름 | 담당 |
| --- | --- |
| 강우진 | 팀장, 공통 개발 환경 및 인프라 |
| 김지혜 | 인증·인가 및 프로필 |
| 김관희 | 의상 관리 |
| 최정윤 | 날씨, 배치 및 성능 |
| 정수용 | 댓글, 좋아요, 알림 및 소셜 기능 |

담당 범위는 프로젝트 진행 상황과 기능 난이도에 따라 조정될 수 있습니다.