# 옷장을 부탁해 Windows Git Bash 실행 가이드

이 문서는 Windows 환경에서 Git Bash를 사용하여 프로젝트를 처음 실행하는 팀원을 위한 가이드입니다.

---

## 1. 필요한 프로그램

다음 프로그램이 설치되어 있어야 합니다.

- Git
- Java 17
- Docker Desktop
- IntelliJ IDEA

Git을 설치하면 Git Bash도 함께 설치됩니다.

프로젝트 폴더에서 마우스 오른쪽 버튼을 누른 뒤 다음 메뉴를 선택하여 Git Bash를 실행할 수 있습니다.

```text
Open Git Bash here
```

Git Bash는 다음과 같은 형태로 표시됩니다.

```text
사용자명@컴퓨터명 MINGW64 ~/프로젝트경로
$
```

---

## 2. 설치 상태 확인

Git Bash에서 다음 명령을 실행합니다.

```bash
java -version
git --version
docker --version
docker compose version
```

Java 버전은 17이어야 합니다.

예시:

```text
openjdk version "17..."
```

Docker 명령이 실행되지 않으면 Docker Desktop이 실행 중인지 확인합니다.

```bash
docker info
```

---

## 3. 저장소 복제

GitHub 저장소 페이지에서 `Code` 버튼을 누른 뒤 HTTPS 주소를 복사합니다.

Git Bash에서 프로젝트를 저장할 폴더로 이동합니다.

예시:

```bash
cd ~/Desktop
```

저장소를 복제합니다.

```bash
git clone 복사한-HTTPS-주소
```

프로젝트 폴더로 이동합니다.

```bash
cd sb11-otboo-team3
```

현재 위치를 확인합니다.

```bash
pwd
```

프로젝트 파일을 확인합니다.

```bash
ls
```

다음과 같은 파일이 보여야 합니다.

```text
build.gradle
compose.yaml
gradlew
gradlew.bat
src
```

이미 저장소를 복제했다면 최신 `develop` 브랜치를 받습니다.

```bash
git switch develop
git pull origin develop
```

---

## 4. Gradle Wrapper 확인

Gradle은 별도로 설치하지 않습니다.

프로젝트에 포함된 Gradle Wrapper를 사용합니다.

```bash
./gradlew --version
```

다음 버전이 표시되는지 확인합니다.

```text
Gradle 8.14.3
JVM 17
```

`./gradlew` 실행이 되지 않으면 다음 명령을 실행합니다.

```bash
chmod +x gradlew
```

다시 확인합니다.

```bash
./gradlew --version
```

계속 실행되지 않으면 Windows용 Gradle Wrapper를 사용합니다.

```bash
./gradlew.bat --version
```

---

## 5. 환경변수 파일 생성

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
cp .env.example .env
```

파일 생성 여부를 확인합니다.

```bash
ls -a
```

목록에 다음 파일이 보여야 합니다.

```text
.env
.env.example
```

기본 환경변수는 다음과 같습니다.

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

기본값을 사용한다면 `.env` 파일을 별도로 수정하지 않아도 됩니다.

`.env`에는 로컬 환경 정보와 민감 정보가 포함될 수 있으므로 Git에 커밋하지 않습니다.

다음 명령으로 `.env`가 Git 변경 파일에 표시되지 않는지 확인합니다.

```bash
git status
```

---

## 6. PostgreSQL과 Redis 실행

Docker Desktop을 먼저 실행합니다.

Docker Desktop이 정상적으로 실행된 상태에서 프로젝트 루트의 Git Bash에서 다음 명령을 실행합니다.

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

컨테이너 로그가 필요한 경우 다음 명령을 실행합니다.

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

로그 확인을 종료할 때는 다음 키를 누릅니다.

```text
Control + C
```

---

## 7. Spring Boot 환경변수 설정

Docker Compose는 프로젝트 루트의 `.env` 파일을 자동으로 읽습니다.

Spring Boot는 `.env` 파일을 직접 자동으로 읽지 않습니다.

기본 `.env` 값은 `application-local.yaml`의 기본값과 동일하므로 값을 변경하지 않았다면 별도의 환경변수 설정 없이 실행할 수 있습니다.

`.env`의 포트, 계정 또는 비밀번호를 변경했다면 필요한 환경변수만 Git Bash에 개별적으로 등록합니다.

```bash
export DB_HOST='localhost'
export DB_PORT='5433'
export DB_NAME='otboo'
export DB_USERNAME='otboo'
export DB_PASSWORD='변경한-비밀번호'

export REDIS_HOST='localhost'
export REDIS_PORT='6380'
```

환경변수 값이 Git Bash에서 임의로 해석되지 않도록 작은따옴표로 감쌉니다.

등록된 값을 확인합니다.

```bash
echo "$DB_HOST"
echo "$DB_PORT"
echo "$DB_NAME"
echo "$REDIS_HOST"
echo "$REDIS_PORT"
```

환경변수를 등록한 Git Bash 창에서 애플리케이션을 실행해야 합니다.

Git Bash를 종료하면 해당 창에 등록한 환경변수도 사라집니다.

비밀번호에 작은따옴표가 포함되어 있거나 Git Bash 환경변수 등록이 어려운 경우에는 IntelliJ Run Configuration의 `Environment variables`에 직접 등록합니다.

---

## 8. 애플리케이션 실행

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
./gradlew bootRun
```

`./gradlew` 실행이 되지 않으면 다음 명령을 사용합니다.

```bash
./gradlew.bat bootRun
```

기본 활성 프로필은 `local`입니다.

정상 실행 주소:

```text
http://localhost:8080
```

실행 로그에 다음과 비슷한 문구가 나타나면 정상입니다.

```text
Started OtbooApplication
```

서버를 종료할 때는 실행 중인 Git Bash에서 다음 키를 누릅니다.

```text
Control + C
```

---

## 9. IntelliJ에서 애플리케이션 실행

Git Bash 대신 IntelliJ에서 애플리케이션을 실행할 수 있습니다.

1. IntelliJ를 실행합니다.
2. `Open`을 선택합니다.
3. `sb11-otboo-team3` 프로젝트 폴더를 선택합니다.
4. Gradle 프로젝트 동기화가 끝날 때까지 기다립니다.
5. Project SDK와 Gradle JVM이 Java 17인지 확인합니다.
6. Spring Boot Application 클래스를 엽니다.
7. 클래스 왼쪽의 실행 버튼을 누릅니다.

### Project SDK 확인

```text
File
→ Project Structure
→ Project
→ SDK
→ Java 17
```

### Gradle JVM 확인

```text
File
→ Settings
→ Build, Execution, Deployment
→ Build Tools
→ Gradle
→ Gradle JVM
→ Java 17
```

기본 `.env` 값을 그대로 사용한다면 IntelliJ 환경변수를 별도로 설정하지 않아도 됩니다.

`.env` 값을 변경했다면 IntelliJ 실행 설정에도 동일한 값을 등록합니다.

```text
Run
→ Edit Configurations
→ Spring Boot 실행 설정
→ Environment variables
```

예시:

```text
DB_HOST=localhost
DB_PORT=5433
DB_NAME=otboo
DB_USERNAME=otboo
DB_PASSWORD=변경한-비밀번호
REDIS_HOST=localhost
REDIS_PORT=6380
```

---

## 10. Swagger 확인

애플리케이션 실행 후 브라우저에서 다음 주소에 접속합니다.

### Swagger UI

```text
http://localhost:8080/swagger-ui/index.html
```

### OpenAPI JSON

```text
http://localhost:8080/v3/api-docs
```

Spring Security 인증 화면이 나타나면 다음 정보를 사용합니다.

```text
사용자 이름: user
비밀번호: 애플리케이션 실행 로그의 generated security password
```

실행 로그에서 다음 문구를 찾습니다.

```text
Using generated security password:
```

인증·인가 기능이 적용되면 Swagger 접근 방식은 변경될 수 있습니다.

---

## 11. 테스트 실행

### 전체 테스트

```bash
./gradlew clean test
```

`./gradlew` 실행이 되지 않으면 다음 명령을 사용합니다.

```bash
./gradlew.bat clean test
```

### CI와 동일한 전체 빌드

```bash
./gradlew clean build
```

GitHub Actions에서도 다음 명령을 기준으로 전체 빌드와 테스트를 실행합니다.

```text
./gradlew clean build
```

테스트 하나라도 실패하면 CI가 실패하고 `develop` 브랜치에 Merge할 수 없습니다.

### 특정 테스트 실행

```bash
./gradlew test --tests "패키지명.테스트클래스명"
```

예시:

```bash
./gradlew test --tests "com.otboo.global.config.OpenApiConfigTest"
```

---

## 12. JaCoCo 커버리지 확인

테스트가 끝나면 다음 위치에 HTML 커버리지 보고서가 생성됩니다.

```text
build/reports/jacoco/test/html/index.html
```

Windows 탐색기로 보고서를 열려면 Git Bash에서 다음 명령을 실행합니다.

```bash
explorer.exe "$(cygpath -w build/reports/jacoco/test/html/index.html)"
```

현재 커버리지는 측정만 하며 80% 미달을 CI 실패나 Merge 차단 조건으로 사용하지 않습니다.

```text
테스트 실패
→ CI 실패
→ Merge 불가

테스트 성공
→ 커버리지와 관계없이 CI 성공
→ 승인과 리뷰 조건 충족 후 Merge 가능
```

---

## 13. Docker 관리

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

## 14. 자주 발생하는 문제

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
netstat.exe -ano | grep ":5432"
```

출력 마지막 열의 숫자가 프로세스 ID입니다.

예시:

```text
TCP    0.0.0.0:5432    0.0.0.0:0    LISTENING    1234
```

프로세스를 확인합니다.

```bash
tasklist.exe /FI "PID eq 1234"
```

기존 PostgreSQL을 중지하거나 `.env`의 포트를 변경합니다.

```dotenv
DB_PORT=5433
```

Docker Compose를 다시 실행합니다.

```bash
docker compose down
docker compose up -d
```

Spring Boot에도 변경한 포트를 적용합니다.

```bash
export DB_PORT='5433'
```

같은 Git Bash 창에서 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

---

### Redis 6379 포트 충돌

6379 포트를 사용 중인 프로세스를 확인합니다.

```bash
netstat.exe -ano | grep ":6379"
```

기존 Redis를 중지하거나 `.env`의 포트를 변경합니다.

```dotenv
REDIS_PORT=6380
```

Docker Compose를 다시 실행합니다.

```bash
docker compose down
docker compose up -d
```

Spring Boot에도 변경한 포트를 적용합니다.

```bash
export REDIS_PORT='6380'
```

같은 Git Bash 창에서 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

---

### 애플리케이션 8080 포트 충돌

8080 포트를 사용 중인 프로세스를 확인합니다.

```bash
netstat.exe -ano | grep ":8080"
```

출력 마지막 열의 프로세스 ID를 확인합니다.

```bash
tasklist.exe /FI "PID eq 프로세스ID"
```

기존 애플리케이션을 종료한 뒤 다시 실행합니다.

---

### PostgreSQL 인증 오류

`.env`의 계정 정보를 변경했는데 기존 PostgreSQL 볼륨에 이전 계정 정보가 남아 있으면 인증 오류가 발생할 수 있습니다.

로컬 데이터를 삭제해도 되는 경우에만 다음 명령을 실행합니다.

```bash
docker compose down -v
docker compose up -d
```

Spring Boot 실행 환경에도 변경한 계정 정보를 개별적으로 등록합니다.

```bash
export DB_USERNAME='변경한-사용자명'
export DB_PASSWORD='변경한-비밀번호'
```

---

### 컨테이너가 healthy 상태가 되지 않음

상태와 로그를 확인합니다.

```bash
docker compose ps
docker compose logs postgres
docker compose logs redis
```

---

### Gradle이 Java 17을 찾지 못함

Java 버전을 확인합니다.

```bash
java -version
```

Java 실행 경로를 확인합니다.

```bash
where.exe java
```

`JAVA_HOME`을 확인합니다.

```bash
echo "$JAVA_HOME"
```

IntelliJ에서는 Project SDK와 Gradle JVM을 모두 Java 17로 설정합니다.

---

### 줄바꿈 문자 오류

Windows와 macOS의 줄바꿈 문자 차이로 다음과 같은 오류가 발생할 수 있습니다.

```text
/bin/sh^M: bad interpreter
```

Git 줄바꿈 설정을 확인합니다.

```bash
git config --global --get core.autocrlf
```

Git Bash에서 셸 스크립트의 LF 줄바꿈을 유지하려면 다음과 같이 설정합니다.

```bash
git config --global core.autocrlf input
```

`input`은 체크아웃된 LF 파일을 CRLF로 변환하지 않고, 커밋할 때 CRLF를 LF로 정규화합니다.

이미 `core.autocrlf=true` 상태로 저장소를 Clone했다면 기존 파일에 CRLF가 적용되어 있을 수 있습니다.

먼저 작업 중인 변경사항이 없는지 확인합니다.

```bash
git status
```

변경사항이 없다면 저장소를 새로 Clone하는 것이 가장 안전합니다.

```bash
cd ..
mv sb11-otboo-team3 sb11-otboo-team3-backup
git clone 복사한-HTTPS-주소
cd sb11-otboo-team3
```

새로 Clone한 저장소에서 다시 실행합니다.

```bash
./gradlew --version
```

정상 실행을 확인한 뒤 기존 백업 폴더는 필요한 파일이 없는지 확인하고 삭제합니다.

---

## 15. Git 작업 시작

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

브랜치 이름 규칙:

```text
feature/{이슈번호}-{기능요약}
bugfix/{이슈번호}-{기능요약}
hotfix/{이슈번호}-{기능요약}
```

`main`과 `develop` 브랜치에는 직접 Push하지 않습니다.

---

## 16. 커밋과 Push

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

커밋 메시지 예시:

```text
Feat: 의상 등록 API 구현 [#12]
Fix: 날씨 조회 중복 반환 오류 수정 [#25]
Docs: 공통 로컬 실행 방법 정리 [#13]
```

---

## 17. Pull Request Merge 조건

`develop` 브랜치에 Merge하려면 다음 조건을 모두 충족해야 합니다.

- Pull Request 생성
- 팀원 승인 2개
- 최신 Push에 대한 승인
- 리뷰 Conversation 해결
- `Build and Test` 통과

테스트가 실패하면 Merge할 수 없습니다.