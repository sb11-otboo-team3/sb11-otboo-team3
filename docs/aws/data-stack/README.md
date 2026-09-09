# EC2 통합 데이터 스택 운영 구성

## 1. 목적

Issue #279에서는 저트래픽 포트폴리오·시연 환경의 AWS 운영 비용을 줄이기 위해
기존 Managed Redis, Kafka, OpenSearch를 하나의 EC2 인스턴스로 통합합니다.

기존 구성:

```text
ECS Fargate
├─ Amazon ElastiCache for Redis OSS
├─ Amazon MSK Provisioned
└─ Amazon OpenSearch Service
```

변경 구성:

```text
ECS Fargate
│
│ VPC Private IP
▼
otboo-prod-data EC2
├─ Redis 7.4
├─ Kafka 3.9.2 KRaft
└─ OpenSearch 1.3.20
```

RDS PostgreSQL, S3, ECS Fargate, ALB, Nginx, Route53, ACM,
CloudWatch 및 GitHub Actions 기반 배포 구조는 유지합니다.

---

## 2. 설계 배경

기존 Managed 서비스는 운영 안정성과 관리 편의성을 우선하여 구성했습니다.

그러나 현재 프로젝트는 다음 조건을 가집니다.

- 제한된 운영 기간
- 낮은 실제 트래픽
- 포트폴리오 및 시연 목적
- Managed Kafka/OpenSearch/Redis 비용 비중이 큼

따라서 고가용성을 일부 포기하는 대신
Redis, Kafka, OpenSearch를 하나의 EC2에 통합해 운영 비용을 줄입니다.

이 구성은 장기 상용 서비스의 권장 고가용성 구성을 의미하지 않습니다.

EC2 한 대 장애 시 Redis, Kafka, OpenSearch가 동시에 영향을 받는
Single Point of Failure가 존재합니다.

---

## 3. EC2 구성

운영 데이터 EC2:

```text
Name: otboo-prod-data
Instance Type: t3.large
Region: ap-northeast-2
Availability Zone: ap-northeast-2a
```

ECS 애플리케이션은 EC2의 Public IP를 사용하지 않습니다.

서비스 연결은 동일 VPC 내부의 EC2 Private IP를 사용합니다.

Private IP는 GitHub Actions 배포 시 EC2 API를 통해 동적으로 조회합니다.

EC2가 재생성되는 경우 Instance ID, IAM 최소 권한,
Security Group 및 배포 설정을 다시 확인해야 합니다.

---

## 4. Security Group

데이터 EC2는 전용 Security Group을 사용합니다.

```text
otboo-prod-data-sg
```

Inbound는 ECS Application Security Group에서만 허용합니다.

```text
TCP 6379  Redis
TCP 9092  Kafka
TCP 9200  OpenSearch
```

허용 소스:

```text
otboo-prod-app-sg
```

다음과 같은 광범위한 접근은 허용하지 않습니다.

```text
0.0.0.0/0
172.31.0.0/16
개인 공인 IP
```

SSH 22 포트도 외부에 개방하지 않습니다.

EC2 운영 접근은 AWS Systems Manager Session Manager를 사용합니다.

---

## 5. Docker Compose 데이터 스택

EC2의 데이터 스택은 다음 경로에서 관리합니다.

```text
/opt/otboo-data
```

구성 서비스:

```text
Redis
Kafka
OpenSearch
```

각 서비스는 Docker named volume을 사용하여
컨테이너 재생성 및 EC2 재부팅 후에도 데이터를 유지합니다.

### Redis

```text
Image: redis:7.4-alpine
Port: 6379
Persistence: AOF
AUTH: enabled
```

Redis AUTH Token은 기존 SSM Parameter Store 값을 재사용합니다.

```text
/otboo/prod/redis/auth-token
```

애플리케이션의 `REDIS_PASSWORD` ECS Secret 주입 방식도 유지합니다.

EC2 Redis는 동일 VPC와 Security Group을 네트워크 경계로 사용하므로
TLS는 사용하지 않습니다.

```text
REDIS_SSL_ENABLED=false
```

### Kafka

```text
Image: apache/kafka:3.9.2
Mode: KRaft
Broker: single node
Port: 9092
Replication Factor: 1
```

운영 애플리케이션 연결:

```text
KAFKA_SECURITY_PROTOCOL=PLAINTEXT
```

Kafka는 외부 인터넷에 공개하지 않고
ECS Application Security Group에서만 9092 접근을 허용합니다.

단일 Broker이므로 Broker 장애 시 Kafka 고가용성을 제공하지 않습니다.

### OpenSearch

```text
Image: opensearchproject/opensearch:1.3.20
Mode: single-node
Port: 9200
Replica: 0
```

현재 애플리케이션의 Elasticsearch REST High Level Client 7.10.2와의
호환성을 우선하여 OpenSearch 1.3 계열을 사용합니다.

Security Plugin은 비활성화하고
동일 VPC + Security Group을 접근 경계로 사용합니다.

장기 운영 시 최신 OpenSearch 및 인증·TLS 적용을 별도 검토합니다.

---

## 6. 자동 시작 및 Ready 기준

EC2 재부팅 시 데이터 스택은 systemd를 통해 자동으로 시작합니다.

```text
otboo-data-stack.service
```

실행 흐름:

```text
EC2 boot
→ Docker service
→ otboo-data-stack.service
→ SSM Parameter Store에서 Redis AUTH Token 조회
→ docker compose up -d
→ Redis health 확인
→ Kafka health 확인
→ OpenSearch health 확인
→ READY
```

다음 조건이 모두 만족되어야 정상 상태로 판단합니다.

```text
otboo-data-stack.service = active

otboo-prod-redis      = healthy
otboo-prod-kafka      = healthy
otboo-prod-opensearch = healthy

Private IP:6379 LISTEN
Private IP:9092 LISTEN
Private IP:9200 LISTEN
```

실제 EC2 재부팅 후 자동 복구와 데이터 영속성을 검증했습니다.

---

## 7. GitHub Actions 배포 Ready Gate

운영 배포는 기존과 동일하게 `develop` push를 기준으로 진행합니다.

```text
PR
→ develop merge
→ CI Quality Gate
→ ECR Git SHA 이미지 Build/Push
→ 데이터 EC2 조회
→ SSM Ready Gate
→ ECS Task Definition render
→ ECS Rolling Deployment
→ ALB / HTTPS Health 검증
```

GitHub Actions는 데이터 EC2의 Private IP에 직접 접근하지 않습니다.

ECS Deploy IAM Role이 SSM Run Command를 사용해
데이터 EC2 내부에서 Ready 상태를 검증합니다.

Ready Gate가 실패하면 ECS 배포를 진행하지 않습니다.

배포 시 다음 환경변수를 현재 EC2 Private IP 기준으로 갱신합니다.

```text
REDIS_HOST=<EC2 Private IP>
REDIS_SSL_ENABLED=false

KAFKA_BOOTSTRAP_SERVERS=<EC2 Private IP>:9092
KAFKA_SECURITY_PROTOCOL=PLAINTEXT

SEARCH_ENDPOINT=http://<EC2 Private IP>:9200
```

기존 Secret, Task Role, Execution Role, DB, S3, Nginx,
CloudWatch 및 나머지 Task Definition 설정은
현재 Live Task Definition을 기반으로 그대로 유지합니다.

---

## 8. Redis 데이터 전환

Redis는 Managed ElastiCache 데이터를 EC2로 복제하지 않고
Cold Cutover를 사용합니다.

캐시 데이터는 재생성합니다.

전환 시 다음 상태는 초기화될 수 있습니다.

- 기존 Refresh Token
- Refresh Token consumed marker
- 진행 중인 Password Reset 상태
- Login Attempt / Block 상태
- Recommendation, Follow, Feed, Weather 관련 Redis Cache

따라서 기존 사용자는 Access Token 만료 이후
재로그인이 필요할 수 있습니다.

RDS에 저장된 영구 데이터에는 영향을 주지 않습니다.

---

## 9. Kafka 데이터 전환

기존 MSK의 Notification Consumer Lag은 전환 전 확인합니다.

운영 전환 직전 다음 조건을 다시 확인합니다.

```text
otboo-notification-created-consumer MaxOffsetLag = 0
```

Notification Outbox의 미처리 상태도 함께 확인합니다.

Weather Prefetch Topic은 기존 MSK에서 생성되지 않아
기존 운영 환경에서 `UNKNOWN_TOPIC_OR_PARTITION`이 발생한 것을 확인했습니다.

EC2 Kafka에는 다음 Topic을 명시적으로 생성했습니다.

```text
otboo.notification.created.v1
otboo.notification.created.v1.dlt
otboo.weather.prefetch-requested.v1
otboo.weather.prefetch-requested.v1.dlt
otboo.infrastructure.connectivity-checked.v1
```

운영 Topic은 단일 Broker 환경에 맞춰
Partition 1, Replication Factor 1을 사용합니다.

---

## 10. OpenSearch 데이터 전환

> **현재 상태**
>
> RDS 원본 Feed 기반 재색인과 실제 Feed 검색 API 검증을 완료했으며,
> 기존 Amazon OpenSearch Service는 최종 운영 검증 이후 삭제했습니다.
> 아래 내용은 Issue #279 전환 당시의 데이터 이관 및 검증 기준을 보존한 기록입니다.

기존 Amazon OpenSearch Service의 인덱스를 직접 Snapshot 이관하지 않습니다.

운영 애플리케이션이 EC2 OpenSearch의 `feeds` 인덱스를 생성한 뒤
RDS의 원본 Feed 데이터를 다시 색인합니다.

관리자 재색인 API:

```text
POST /api/admin/feeds/search/reindex
```

전환 후 다음을 함께 검증합니다.

```text
RDS의 삭제되지 않은 Feed 수
재색인 API 처리 수
OpenSearch feeds _count
실제 Feed 검색 API 결과
```

재색인과 실제 검색 기능 검증이 끝나기 전에는
기존 Amazon OpenSearch Service를 삭제하지 않습니다.

---

## 11. Rollback

> **현재 상태**
>
> Amazon ElastiCache, Amazon MSK, Amazon OpenSearch Service는
> EC2 Data Stack 전환과 실제 기능 검증 완료 후 모두 삭제했습니다.
>
> 따라서 아래 Managed 서비스 기반 Rollback 경로는
> **현재 운영 환경에서는 더 이상 사용할 수 없습니다.**
> 이 절은 Issue #279 전환 당시의 Rollback 설계와 안정화 기준을
> 운영 이력으로 보존합니다.

기존 Managed 서비스는 EC2 전환 직후 즉시 삭제하지 않습니다.

안정화 기간 동안 다음 리소스를 Rollback 후보로 유지합니다.

```text
Amazon ElastiCache
Amazon MSK
Amazon OpenSearch Service
기존 ECS Task Definition Revision
```

다만 기존 Task Definition으로 되돌리는 것만으로
EC2 전환 이후 생성된 Stateful 데이터를 복구할 수 있는 것은 아닙니다.

Rollback 절차는 전환 시점에 따라 구분합니다.

### 11.1 EC2 데이터 쓰기 전 배포 실패

새 ECS Task가 정상 서비스에 진입하기 전에 실패하여
Redis, Kafka, OpenSearch에 운영 데이터가 기록되지 않은 경우에는
기존 Managed endpoint를 사용하는 Task Definition으로 되돌릴 수 있습니다.

```text
새 Task 배포 실패
→ Deployment Circuit Breaker
→ 기존 Task Definition 복구
→ 기존 Managed Redis / Kafka / OpenSearch 사용
```

이 구간에서는 기존 Managed 서비스가 계속 최신 운영 상태이므로
기존 ECS Task Definition 기반 Rollback이 기본 복구 경로입니다.

### 11.2 EC2 데이터 쓰기 시작 후 Rollback

새 ECS Task가 정상 서비스에 진입하고
EC2 Redis, Kafka 또는 OpenSearch에 운영 데이터가 기록된 이후에는
기존 Task Definition으로 즉시 되돌리지 않습니다.

먼저 애플리케이션 쓰기를 중지하고 상태를 확인합니다.

```text
운영 쓰기 중지
→ EC2 Kafka 처리 상태 확인
→ Notification Outbox 확인
→ Redis 상태 영향 확인
→ 검색 인덱스 정합성 확인
→ Managed 서비스 복귀 여부 결정
```

#### Kafka

EC2 Kafka로 발행된 메시지는 기존 Amazon MSK에 자동 복제되지 않습니다.

따라서 Rollback 전에 다음을 확인합니다.

```text
Notification Consumer Lag = 0
처리 중 메시지 없음
Notification Outbox pending / failed 상태 확인
DLT 미처리 메시지 확인
```

EC2 Kafka에 이미 발행된 메시지가 남아 있는 상태에서
MSK 기반 Task로 되돌리면 해당 메시지가 처리되지 않을 수 있습니다.

가능한 경우 EC2 Kafka의 처리 대상 메시지를 모두 Drain한 후 Rollback합니다.

Outbox와 실제 Consumer 처리 결과를 확인하지 않은 상태에서
동일 이벤트를 MSK로 다시 발행하지 않습니다.
중복 처리 가능성이 있기 때문입니다.

#### Redis

EC2 Redis와 기존 ElastiCache 사이에는
상태를 양방향 동기화하지 않습니다.

Rollback 시 다음 상태는 유지되지 않을 수 있습니다.

```text
Refresh Token
Refresh Token consumed marker
Password Reset 상태
Login Attempt / Block 상태
각종 Cache
```

Redis 상태 손실은 허용된 Cold Cutover 특성으로 취급하며,
필요한 경우 사용자는 다시 로그인하고 Cache는 재생성합니다.

RDS의 영구 데이터는 Redis Rollback 대상이 아닙니다.

#### OpenSearch

EC2 OpenSearch에 반영된 검색 인덱스 변경은
기존 Amazon OpenSearch Service에 자동 반영되지 않습니다.

따라서 Managed OpenSearch로 복귀하는 경우
RDS의 원본 Feed 데이터를 기준으로 다시 재색인합니다.

```text
RDS 원본 Feed 확인
→ Managed OpenSearch 재색인
→ 문서 수 확인
→ 실제 검색 API 확인
```

검색 결과 검증이 완료되기 전에는
Rollback 완료로 판단하지 않습니다.

### 11.3 Rollback 완료 기준

다음 조건을 확인한 뒤 운영 복구 완료로 판단합니다.

```text
ECS Service stable
ALB Target healthy
HTTPS Health Check 정상
Notification 처리 상태 정상
Redis 영향 범위 확인
검색 인덱스 재색인 및 검색 검증 완료
```

Managed 서비스 삭제 후에는 이 Rollback 경로를 사용할 수 없습니다.

따라서 실제 기능 검증과 안정화,
Stateful 서비스의 복귀 필요성이 없음을 확인한 뒤
기존 Managed 서비스를 삭제합니다.

---

## 12. 가용성 및 보안 Trade-off

기존 Managed 구성과 비교해 다음 보호 수준이 낮아집니다.

```text
Kafka
IAM + SASL_SSL
→ VPC/SG + PLAINTEXT

Redis
TLS + AUTH
→ VPC/SG + AUTH

OpenSearch
HTTPS + AWS Managed Encryption
→ VPC/SG + HTTP
```

또한 Redis, Kafka, OpenSearch가 하나의 EC2에 위치하므로
EC2 장애가 세 서비스에 동시에 영향을 줍니다.

이 Trade-off는 현재 프로젝트의 낮은 트래픽과
운영 비용 절감을 위해 의도적으로 선택했습니다.

고가용성, 서비스별 독립 장애 격리, 전송구간 암호화가 중요한
장기 상용 환경에서는 Managed 서비스 또는 다중 노드 구조를 검토합니다.


### 12.1 전송 암호화 제거에 대한 위험 수용

Issue #279의 EC2 통합 구성에서는 운영 비용 절감을 위해
Redis, Kafka, OpenSearch의 전송 구간 암호화 수준을 기존 Managed 구성보다 낮춥니다.

적용 범위:

```text
Redis
REDIS_SSL_ENABLED=false
AUTH 유지

Kafka
KAFKA_SECURITY_PROTOCOL=PLAINTEXT

OpenSearch
SEARCH_ENDPOINT=http://<EC2 Private IP>:9200
Security Plugin disabled
```

이 구성에서는 동일 VPC 내부 네트워크 구간에서
Redis 인증정보, Kafka 메시지, OpenSearch 요청과 응답이
TLS로 암호화되지 않습니다.

위험을 줄이기 위해 다음 네트워크 제한을 필수 조건으로 유지합니다.

```text
EC2 Public endpoint 사용 금지
ECS → EC2 Private IP 연결
Data Security Group 인바운드 소스 = ECS Application Security Group
허용 포트 = 6379 / 9092 / 9200
0.0.0.0/0 인바운드 금지
VPC 전체 CIDR 인바운드 금지
SSH 22 포트 공개 금지
EC2 운영 접근 = AWS Systems Manager
```

이 Trade-off는 저트래픽 포트폴리오·시연 환경의
비용 최적화를 위한 프로젝트 범위의 결정입니다.

승인 주체는 프로젝트 운영 및 인프라 담당자이며,
Issue #279와 해당 Pull Request를 변경 승인 기록으로 사용합니다.

다음 조건 중 하나라도 발생하면 현재 위험 수용 범위를 종료하고
TLS 및 서비스 인증 구성을 다시 검토합니다.

```text
장기 상용 운영으로 전환
외부 사용자 또는 트래픽 규모 증가
민감 데이터 처리 범위 증가
Security Group 허용 범위 확대 필요
VPC 외부 또는 다른 네트워크에서 접근 필요
EC2 데이터 스택을 다중 사용자 환경에서 공동 사용
보안 사고 또는 비인가 접근 의심
```

보안 사고 또는 비인가 접근이 의심되는 경우에는
우선 데이터 EC2로의 애플리케이션 쓰기를 중지하고
Security Group 접근 범위를 차단하거나 축소합니다.

이후 다음 순서로 대응합니다.

```text
ECS → Data EC2 트래픽 제한
→ CloudWatch / ECS / EC2 관련 로그 확인
→ Redis AUTH Token 폐기 및 재발급
→ 영향받은 Redis 상태 초기화
→ Kafka / OpenSearch 데이터 영향 범위 확인
→ EC2 Data Stack 복구 또는 RDS 원본 기반 재색인 / 재구성 검토
→ 원인 제거 후 재배포
```

현재 구성의 보안 경계가 유지되지 않는 상태에서는
비용 절감을 이유로 PLAINTEXT / HTTP 구성을 계속 사용하지 않습니다.

---

## 13. Managed 리소스 제거 결과

다음 조건을 모두 검증한 뒤 기존 Managed 서비스 3종을 삭제했습니다.

```text
ECS → EC2 Private IP 연결 검증
Redis 주요 기능 검증
Kafka Produce / Consume / DLT 검증
Weather Prefetch 검증
OpenSearch 재색인 검증
실제 검색 API 검증
Rolling Deployment 정상
운영 안정화 확인
Rollback 필요성 해소
```

삭제 완료:

```text
Amazon ElastiCache
Amazon MSK Provisioned
Amazon OpenSearch Service
```


RDS PostgreSQL과 Amazon S3는 이번 비용 최적화 대상에서 제외했으며
현재 운영 구성에서 계속 사용합니다.

---

## 14. 운영 스케줄

저트래픽 포트폴리오·시연 환경의 실제 사용 시간을 기준으로
EventBridge Scheduler를 사용해 주요 Compute 리소스의 운영 시간을 제한합니다.

| 시간 | 평일 운영 |
| --- | --- |
| `07:15` | RDS 시작 |
| `07:15` | Data EC2 시작 |
| `07:45` | ECS Desired Count `1` |
| `08:15` | Weather Prefetch |
| `11:15` | Weather Prefetch |
| `14:15` | Weather Prefetch |
| `17:15` | Weather Prefetch |
| `20:15` | Weather Prefetch |
| `20:45` | Weather Cleanup |
| `21:00` | Feed Cleanup |
| `21:15` | DM Cleanup |
| `21:30` | Outbox Cleanup |
| `22:00` | ECS Desired Count `0` |
| `22:10` | Data EC2 중지 |
| `22:15` | RDS 중지 |

주말에는 ECS, RDS, Data EC2를 운영하지 않습니다.

시작 시 RDS와 Data EC2는 서로 독립적이므로 동시에 시작하고,
Data EC2의 `systemd` 서비스가 Redis, Kafka, OpenSearch를 자동으로 기동합니다.

종료 시에는 애플리케이션과 데이터 계층의 의존성을 고려하여
다음 순서를 유지합니다.

```text
ECS
→ Data EC2
→ RDS
```

GitHub Actions의 Data Stack Ready Gate는
Data EC2가 실행 중이고 Redis, Kafka, OpenSearch가 정상 상태일 때만
ECS 배포를 진행합니다.

따라서 Data EC2 운영시간 외에 배포할 경우
먼저 Data EC2와 데이터 스택을 시작해야 합니다.

---

## 15. 비용 최적화 검증 결과

AWS Cost Explorer의 `UnblendedCost`를 기준으로
전환 전 정상 평일과 전환 후 정상 평일 비용을 비교했습니다.

### 변경 전

```text
2026-09-02  US$6.7146
2026-09-03  US$6.7948

평균          약 US$6.75 /일
```

### 변경 후

```text
2026-09-08  US$2.6956

약 US$2.70 /일
```

### 절감 결과

| 항목 | 결과 |
| --- | ---: |
| 변경 전 평일 평균 | 약 `US$6.75 /일` |
| 변경 후 평일 비용 | 약 `US$2.70 /일` |
| 평일 일 절감액 | 약 `US$4.06` |
| 평일 기준 절감률 | 약 `60.1%` |
| 평일 20일 단순 환산 | 약 `US$81.2 /월 절감` |

비용 절감의 핵심 구조는 다음과 같습니다.

```text
Amazon ElastiCache
Amazon MSK Provisioned
Amazon OpenSearch Service
        ↓
EC2 Data Stack
Redis + Kafka + OpenSearch
```

기존 Managed 3종의 비용을 하나의 Data EC2로 통합하고,
ECS와 RDS를 포함한 주요 Compute 리소스도
실제 사용 시간에 맞춰 평일에만 제한적으로 운영합니다.

비용 조회 당시 Cost Explorer의 일별 값은 `Estimated=true`였으므로
최종 청구 과정에서 소폭 조정될 수 있습니다.

또한 `US$81.2 /월`은
평일 일 절감액 `US$4.06 × 20일`의 단순 환산값입니다.

실제 월 비용은 해당 월의 평일 수와 트래픽,
ALB, EBS, ECR, VPC 등 상시 또는 별도 과금 항목에 따라 달라질 수 있습니다.
