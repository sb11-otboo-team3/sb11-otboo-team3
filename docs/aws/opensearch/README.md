# Elasticsearch / OpenSearch 운영 구성

> **운영 상태 안내**
>
> 이 문서는 기존 Amazon OpenSearch Service 기반 운영 검색 환경의
> 설계와 검증 이력을 보존하기 위한 문서입니다.
>
> Issue #279에서는 AWS 운영 비용 최적화를 위해 운영 OpenSearch를
> EC2 통합 데이터 스택의 단일 OpenSearch 노드로 전환합니다.
>
> 전환 후 애플리케이션은 EC2 Private IP의 OpenSearch `9200` 포트에
> HTTP로 연결합니다.
>
> ```text
> 기존 Amazon OpenSearch Service
> HTTPS :443
> VPC + Security Group
> AWS Managed Encryption
>
> Issue #279 EC2 OpenSearch
> HTTP :9200
> VPC + Security Group
> Single Node
> ```
>
> EC2 OpenSearch는 `opensearchproject/opensearch:1.3.20`을 사용하며
> 현재 Elasticsearch REST High Level Client 7.10.2와의 호환성을 유지합니다.
>
> 기존 Amazon OpenSearch Service의 인덱스를 직접 이전하지 않고,
> RDS의 원본 Feed 데이터를 관리자 재색인 API를 통해 다시 색인합니다.
>
> 전환 직후에는 기존 Amazon OpenSearch Service를 Rollback 대상으로 유지했습니다.
> 이후 RDS 원본 Feed 재색인, EC2 OpenSearch 문서 수,
> 실제 Feed 검색 API 및 EC2 Stop / Start 후 데이터 유지를 검증한 뒤
> 기존 Amazon OpenSearch Service를 삭제했습니다.
>
> 따라서 현재 운영 환경에서는 Amazon OpenSearch Service를 사용하지 않으며,
> 아래 Managed OpenSearch 구성 내용은 전환 이전 운영 이력으로만 유지합니다.
>
> 현재 운영 데이터 스택 기준은
> [EC2 통합 데이터 스택 운영 구성](../data-stack/README.md)을 참고합니다.


> **#279 전환 전 구성 범위**
>
> 아래 `## 1`부터 이어지는 Amazon OpenSearch Service Endpoint,
> HTTPS `443`, TLS, Domain Access Policy, Subnet, Security Group 및
> ECS 연동 값은 모두 **Issue #279 전환 이전의 Managed OpenSearch 운영 구성**을
> 기록한 것입니다.
>
> Issue #279 이후 현재 운영 기준은
> [EC2 통합 데이터 스택 운영 구성](../data-stack/README.md)을 따릅니다.

## 1. 목적 및 범위

피드 검색 기능의 Full Text Search를 지원하기 위한 검색 인프라를 구성합니다.

개발 환경에서는 Docker 기반 Elasticsearch OSS를 사용하고,
Issue #279 전환 이전 운영 환경에서는 Amazon OpenSearch Service를 사용했습니다.

구성 범위는 다음과 같습니다.

- 로컬 Elasticsearch 실행 환경
- Elasticsearch / OpenSearch 호환 버전 및 Client 기준
- AWS OpenSearch Service 운영 환경
- VPC, Subnet, Security Group 구성
- OpenSearch Domain Access Policy
- ECS `prod` 환경 연결
- 검색 Client Timeout 기준
- 단일 노드 Replica 운영 기준
- 운영 모니터링 및 장애 확인 기준
- 백업 및 복구 기준
- 운영 종료 시 리소스 정리 절차

다음 항목은 피드 검색 기능 담당 범위이며 본 인프라 구성에서는 구현하지 않습니다.

- 피드 인덱스 및 매핑
- 피드 등록·수정·삭제 시 인덱스 동기화
- 기존 데이터 재색인
- Full Text Search 쿼리
- 기존 검색 필터와 Full Text Search 결합
- 검색 기능 테스트

---

## 2. 버전 및 Client 기준

### 로컬

- Elasticsearch OSS `7.10.2`
- Docker 기반 단일 노드 구성

### 운영

- Amazon OpenSearch Service
- OpenSearch `1.3`

### Java Client

- `elasticsearch-rest-high-level-client:7.10.2`
- `elasticsearch-rest-client:7.10.2`

Spring Boot에서 제공하는 최신 Elasticsearch Client를 그대로 사용하지 않고,
로컬 Elasticsearch OSS 7.10.2와 운영 OpenSearch 1.x 사이의 호환성을 고려하여
Elasticsearch OSS 7.10.2 계열 Client를 명시적으로 사용합니다.

### 버전 지원 기준

OpenSearch 오픈소스 프로젝트의 1.x maintenance는 종료되었지만,
운영 환경은 self-managed OpenSearch가 아닌 Amazon OpenSearch Service를 사용합니다.

현재 AWS OpenSearch Service 지원 정책상 OpenSearch 1.3의
Standard Support 종료 일정은 발표되지 않았습니다.

Elasticsearch REST High Level Client는 deprecated 상태이므로
현재 구성은 로컬 Elasticsearch OSS 7.10.2와 운영 OpenSearch 1.3 간
호환성을 우선한 프로젝트 범위의 구성으로 사용합니다.

장기 운영 또는 검색 인프라 업그레이드 시에는
OpenSearch Java Client와 최신 OpenSearch 버전으로의 전환을 검토합니다.

---

## 3. 로컬 Elasticsearch

로컬 개발 환경에서는 Docker Compose로 Elasticsearch를 실행합니다.

### 실행

```bash
docker compose up -d elasticsearch
```

### 상태 확인

```bash
docker compose ps elasticsearch
```

### 서버 확인

```bash
curl http://localhost:9200
```

현재 기준:

- Elasticsearch OSS `7.10.2`
- Cluster: `otboo-local-search`
- Node: `otboo-local-elasticsearch`
- Endpoint: `http://localhost:9200`

### Cluster Health 확인

```bash
curl "http://localhost:9200/_cluster/health?pretty"
```

로컬 환경에서는 `SEARCH_ENDPOINT`가 지정되지 않으면 다음 주소를 기본값으로 사용합니다.

`http://localhost:9200`

---

## 4. 운영 OpenSearch 구성

운영 검색 인프라는 Amazon OpenSearch Service를 사용합니다.

| 항목 | 값 |
| --- | --- |
| Domain | `otboo-prod-search` |
| Region | `ap-northeast-2` |
| Engine | `OpenSearch 1.3` |
| Instance Type | `t3.small.search` |
| Instance Count | `1` |
| Dedicated Master | 비활성 |
| Zone Awareness | 비활성 |
| Multi-AZ | 비활성 |
| EBS | `gp3` |
| Volume Size | `10 GiB` |
| IOPS | `3000` |
| Throughput | `125 MiB/s` |

현재 프로젝트의 제한된 운영 기간과 비용을 고려하여
Single-AZ 단일 데이터 노드 구성을 사용합니다.

단일 데이터 노드는 Replica 기반 고가용성을 제공하지 않습니다.

고가용성이 필요한 실제 장기 운영 서비스에서는
Multi-AZ 및 다중 데이터 노드 구성을 별도로 검토해야 합니다.

---

## 5. 네트워크 구성

OpenSearch Domain은 Public Access 방식이 아닌 VPC 방식으로 구성합니다.

### VPC

`vpc-00db8f2d24cb038f9`

CIDR:

`172.31.0.0/16`

### OpenSearch Subnet

`subnet-08530421cc2349e5b`

- Name: `otboo-prod-db-private-2a`
- AZ: `ap-northeast-2a`
- CIDR: `172.31.64.0/24`
- Public IP 자동 할당: 비활성

### ECS Application Security Group

`sg-0721e7157c9051e76`

Name:

`otboo-prod-app-sg`

### OpenSearch Security Group

`sg-057d137548110f0e1`

Name:

`otboo-prod-opensearch-sg`

Inbound는 다음 규칙만 허용합니다.

- Protocol: TCP
- Port: `443`
- Source: `sg-0721e7157c9051e76`

구조는 다음과 같습니다.

ECS Task  
→ `otboo-prod-app-sg`  
→ TCP 443  
→ `otboo-prod-opensearch-sg`  
→ OpenSearch

인터넷에서 OpenSearch Domain으로 직접 접근할 수 있도록 구성하지 않습니다.

2026-08-25에는 운영 ECS와 동일한 Subnet 및
`otboo-prod-app-sg`를 사용하는 일회성 Fargate Task에서
OpenSearch REST API 호출에 성공하여
ECS 네트워크 영역에서 OpenSearch에 접근 가능한 것도 확인했습니다.

---

## 6. 보안 구성

운영 OpenSearch에는 다음 보안 설정을 적용합니다.

- VPC 기반 Private 접근
- OpenSearch 전용 Security Group
- ECS App Security Group에서만 TCP 443 접근 허용
- HTTPS 강제
- 최소 TLS 1.2
- Encryption at Rest 활성화
- Node-to-node Encryption 활성화

현재 Domain 설정:

- `EnforceHTTPS = true`
- `TLSSecurityPolicy = Policy-Min-TLS-1-2-2019-07`
- `EncryptionAtRest = true`
- `NodeToNodeEncryption = true`

### Domain Access Policy

2026-08-25 운영 Domain Access Policy를 재검토하고
기존의 전체 OpenSearch 권한인 `es:*`를
HTTP 요청에 필요한 `es:ESHttp*`로 축소했습니다.

현재 정책의 핵심 값은 다음과 같습니다.

```json
{
  "Effect": "Allow",
  "Principal": {
    "AWS": "*"
  },
  "Action": "es:ESHttp*",
  "Resource": "arn:aws:es:ap-northeast-2:310567229825:domain/otboo-prod-search/*"
}
```

적용 상태 확인 결과:

- State: `Active`
- PendingDeletion: `false`
- UpdateVersion: `13`

현재 애플리케이션의 Elasticsearch REST High Level Client는
AWS SigV4 요청 서명을 적용하지 않고 있습니다.

따라서 현재 구성에서 Domain Access Policy의 Principal을
ECS Task Role로 제한하면 애플리케이션 요청에 SigV4 인증 구성이 추가로 필요합니다.

이번 프로젝트 범위에서는 애플리케이션 SigV4 인증을 추가하지 않고,
다음 두 계층을 조합해 접근 범위를 제한합니다.

1. Domain Access Policy에서는 HTTP 동작을 `es:ESHttp*`로 제한
2. 네트워크에서는 VPC와 Security Group을 사용하여
   ECS App Security Group에서만 OpenSearch 443 포트 접근 허용

따라서 `Principal = "*"`은 인터넷 전체에 OpenSearch를 공개한다는 의미가 아니며,
실제 네트워크 접근은 VPC와 Security Group에 의해 제한됩니다.

장기 운영 서비스에서는
AWS SigV4 기반 인증을 적용한 뒤
Domain Access Policy의 Principal을 ECS Task Role 등으로 제한하는 방안을 추가 검토합니다.

### 현재 보안 범위

현재 프로젝트에서는 다음 항목을 적용하지 않습니다.

- Fine-grained Access Control
- 애플리케이션 SigV4 요청 서명
- IAM Principal 기반 애플리케이션 요청 제한

실제 장기 운영 서비스에서는 IAM 기반 인증 및
세분화된 접근 제어 적용을 추가 검토해야 합니다.

---

## 7. ECS 운영 환경 연결

Spring Boot 검색 Client는 다음 환경변수를 통해 검색 Endpoint를 주입받습니다.

`SEARCH_ENDPOINT`

운영 Endpoint 형식:

`https://<OpenSearch VPC Endpoint>`

현재 운영 Domain Endpoint는 저장소에 직접 기록하지 않습니다.

필요한 경우 AWS CLI로 조회합니다.

OpenSearch Endpoint는 비밀번호나 API Key가 아니므로
Secrets Manager 또는 Parameter Store의 Secret으로 관리하지 않고
ECS 일반 환경변수로 전달합니다.

### ECS Task Definition

Family:

`otboo-prod-backend`

OpenSearch 연결 환경변수가 최초 반영된 Revision:

`45`

Backend Container:

`otboo-backend`

Revision 45에는 기존 운영 설정을 유지하면서
다음 환경변수만 추가했습니다.

`SEARCH_ENDPOINT`

Nginx sidecar에는 검색 관련 환경변수를 전달하지 않습니다.

2026-08-25 확인 기준 운영 ECS Service는 다음 Task Definition을 사용하고 있습니다.

`otboo-prod-backend:70`

Revision 70의 `otboo-backend` 컨테이너에도
`SEARCH_ENDPOINT`가 설정되어 있는 것을 확인했습니다.

---

## 8. 검색 Client Timeout 기준

OpenSearch 장애 또는 지연 상황에서
애플리케이션 요청 스레드가 검색 응답을 장시간 기다리지 않도록
검색 Client에 명시적인 Timeout을 적용합니다.

### 운영 기준

- Connection Timeout: `3초`
- Socket / Response Timeout: `5초`

설정은 공통 `application.yaml`에서 관리합니다.

```yaml
app:
  search:
    connect-timeout: 3s
    socket-timeout: 5s
```

환경별 Endpoint는 기존 프로필 설정을 사용합니다.

로컬:

```yaml
app:
  search:
    endpoint: ${SEARCH_ENDPOINT:http://localhost:9200}
```

운영:

```yaml
app:
  search:
    endpoint: ${SEARCH_ENDPOINT}
```

### Connection Timeout

Connection Timeout은
OpenSearch 서버와 TCP 연결을 생성할 때 기다리는 최대 시간입니다.

네트워크 장애 또는 Endpoint 연결 불가 상황에서
연결 시도가 무한정 대기하지 않도록 `3초`를 기준으로 사용합니다.

### Socket / Response Timeout

Socket Timeout은
연결된 OpenSearch 서버로부터 응답 데이터를 기다리는 최대 시간입니다.

OpenSearch의 처리 지연이나 응답 정체가
애플리케이션 요청을 장시간 점유하지 않도록 `5초`를 기준으로 사용합니다.

### Client 적용

Elasticsearch REST High Level Client 7.10.2의
`RestClient.builder()`에 `RequestConfigCallback`을 적용합니다.

```java
RestClient.builder(HttpHost.create(searchProperties.endpoint()))
        .setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(connectTimeoutMillis)
                        .setSocketTimeout(socketTimeoutMillis)
        );
```

현재 #253 범위에서는 다음 두 Timeout만 명시적으로 관리합니다.

- Connect Timeout
- Socket Timeout

Connection Pool에서 연결을 대여하기까지의 대기 시간인
`connectionRequestTimeout`은 현재 프로젝트 범위에 추가하지 않습니다.

### 테스트

`SearchConfigTest`에서 다음을 검증합니다.

- `connect-timeout=3s`가 `Duration.ofSeconds(3)`으로 바인딩되는지 확인
- `socket-timeout=5s`가 `Duration.ofSeconds(5)`로 바인딩되는지 확인
- 지연 응답을 반환하는 로컬 HTTP Server를 사용하여
  실제 Elasticsearch Client에 Socket Timeout이 적용되는지 확인

테스트에서는 서버 응답을 의도적으로 지연시키고
Socket Timeout을 `100ms`로 설정하여
지정된 시간 안에 `IOException`이 발생하는 것을 확인합니다.

이를 통해 OpenSearch 장애 또는 지연 상황에서
검색 요청이 무기한 점유되지 않고
설정된 Timeout에 의해 종료되는 동작을 코드 레벨에서 검증합니다.

실제 로컬 Elasticsearch OSS 7.10.2를 실행한 상태에서도
다음 통합 테스트가 정상 통과하는 것을 확인했습니다.

`SearchConnectionIntegrationTest`

---

## 9. ECS 배포 기준

운영 Task Definition을 변경할 때 저장소의 참조용 JSON을
그대로 AWS에 등록하지 않습니다.

현재 ECS Service가 실제로 사용하는 Live Task Definition을 조회하고,
기존 다음 설정을 모두 유지한 상태에서 필요한 변경만 적용합니다.

- Backend Image
- Nginx Sidecar
- Task Role
- Task Execution Role
- CPU / Memory
- Port Mapping
- Log Configuration
- 기존 환경변수
- Secrets

GitHub Actions 자동 배포에서도 현재 ECS Service의 Live Task Definition을 기준으로
Backend Image만 새로운 Git SHA 이미지로 교체합니다.

따라서 운영 환경변수와 Nginx sidecar 등의 Live 설정을 유지할 수 있습니다.

---

## 10. 단일 노드 Replica 운영 기준

Issue #279 전환 이전 Amazon OpenSearch Service는 Data Node가 1개인 단일 노드 구성으로 운영했습니다.

OpenSearch의 Replica Shard는
Primary Shard와 동일한 노드에 배치될 수 없습니다.

따라서 단일 노드 환경에서 Replica를 `1` 이상으로 설정하면
Replica Shard가 할당되지 않고
Cluster Health가 `Yellow` 상태가 될 수 있습니다.

현재 프로젝트의 단일 노드 운영 기준은 다음과 같습니다.

```text
number_of_replicas = 0
```

### 현재 운영 설정 확인

2026-08-25 운영 OpenSearch에 실제로 존재하는 인덱스를 조회한 결과:

```text
health  status  index       pri  rep
green   open    .kibana_1    1    0
```

`.kibana_1`의 실제 `_settings` 조회 결과:

```json
{
  ".kibana_1": {
    "settings": {
      "index": {
        "number_of_shards": "1",
        "number_of_replicas": "0"
      }
    }
  }
}
```

따라서 현재 운영 상태에서는:

- Primary Shard: `1`
- Replica Shard: `0`
- Index Health: `Green`
- Unassigned Shard: `0`

상태가 일치하는 것을 확인했습니다.

### 애플리케이션 인덱스 기준

피드 등 향후 생성되는 애플리케이션 검색 인덱스도
현재의 단일 데이터 노드 구성을 사용하는 동안에는
다음 값을 운영 기준으로 사용합니다.

```text
number_of_replicas = 0
```

피드 인덱스 생성 및 매핑 자체는
피드 검색 기능 담당 범위이므로
본 인프라 작업에서는 인덱스 생성 코드를 추가하지 않습니다.

애플리케이션 인덱스를 생성하는 작업에서는
현재 운영 환경이 단일 노드임을 고려하여
Replica 값을 명시적으로 `0`으로 설정해야 합니다.

향후 데이터 노드를 2개 이상으로 확장하거나
Multi-AZ 구성을 적용하는 경우에는
Replica 수를 다시 검토합니다.

### 고가용성 주의사항

`number_of_replicas=0`은
단일 노드에서 Unassigned Replica를 발생시키지 않기 위한 운영 기준입니다.

이 설정은 고가용성을 제공하지 않습니다.

현재 Data Node 자체에 장애가 발생하면
다른 노드의 Replica로 자동 전환할 수 없으므로,
복구는 OpenSearch Snapshot 또는 PostgreSQL 원본 데이터 기반 재색인 정책에 의존합니다.

---

## 11. 운영 상태 확인

### Domain 상태

```bash
aws opensearch describe-domain-health \
  --domain-name otboo-prod-search \
  --region ap-northeast-2 \
  --query '{
    DomainState:DomainState,
    ClusterHealth:ClusterHealth,
    DataNodeCount:DataNodeCount,
    MasterEligibleNodeCount:MasterEligibleNodeCount,
    TotalShards:TotalShards,
    TotalUnAssignedShards:TotalUnAssignedShards
  }' \
  --output table
```

정상 확인 기준:

- `DomainState = Active`
- `ClusterHealth = Green`
- `DataNodeCount = 1`
- `TotalUnAssignedShards = 0`

2026-08-19 운영 구성 직후 확인 결과:

- Domain State: `Active`
- Cluster Health: `Green`
- Data Node Count: `1`
- Master Eligible Node Count: `1`
- Total Shards: `1`
- Unassigned Shards: `0`

현재 단일 데이터 노드 구성에서는
애플리케이션 인덱스의 `number_of_replicas=0`을 운영 기준으로 사용합니다.

따라서 정상 상태에서는
Replica Shard가 할당되지 않아 발생하는 `Yellow` 상태가 없어야 하며,
다음 조건을 함께 만족하는 것을 정상 기준으로 사용합니다.

- Cluster Health: `Green`
- Total Unassigned Shards: `0`

---

## 12. 모니터링 및 장애 확인 기준

운영 중에는 CloudWatch에서 다음 OpenSearch 지표를 우선 확인합니다.

- `ClusterStatus.red`
- `ClusterStatus.yellow`
- `FreeStorageSpace`
- `ClusterIndexWritesBlocked`
- `CPUUtilization`
- `JVMMemoryPressure`
- `AutomatedSnapshotFailure`

### 우선 확인 순서

검색 장애 발생 시 다음 순서로 확인합니다.

1. OpenSearch Domain 상태가 `Active`인지 확인
2. Cluster Health가 `Red` 또는 `Yellow`인지 확인
3. Unassigned Shard가 발생했는지 확인
4. 단일 노드 인덱스의 Replica가 `0`인지 확인
5. Free Storage Space 확인
6. JVM Memory Pressure 확인
7. CPU 사용률 확인
8. Index Write Block 여부 확인
9. ECS Application 로그에서 검색 Client Timeout 또는 연결 오류 확인
10. ECS App SG와 OpenSearch SG 규칙 확인

현재 EBS 크기는 10 GiB이므로
Free Storage Space 감소를 특히 주의해서 확인합니다.

### Timeout 관련 장애 확인

검색 Client에서 다음 유형의 오류가 반복되는 경우
OpenSearch 상태와 네트워크를 함께 확인합니다.

- Connection Timeout
- Socket Timeout
- Connection refused
- DNS 또는 Endpoint 연결 오류

Connection Timeout은 OpenSearch까지 연결을 생성하지 못하는 상황을 의미할 수 있으며,
Socket Timeout은 연결 후 OpenSearch 응답이 설정된 시간 안에 도착하지 않은 상황을 의미할 수 있습니다.

검색 기능 장애가 발생하더라도
애플리케이션 요청이 OpenSearch 응답을 무기한 기다리지 않도록
현재 Client는 Connection Timeout `3초`,
Socket Timeout `5초`를 사용합니다.

---

## 13. 백업 및 복구 기준

OpenSearch는 검색을 위한 인덱스 저장소이며
서비스의 원본 데이터 저장소는 PostgreSQL입니다.

따라서 복구 기준은 다음 순서로 사용합니다.

### 1차 복구

Amazon OpenSearch Service의 자동 Snapshot을 이용한 복원을 검토합니다.

### 2차 복구

Snapshot을 사용할 수 없거나 검색 인덱스를 새로 구성해야 하는 경우
PostgreSQL에 저장된 원본 데이터를 기준으로 전체 재색인을 수행합니다.

재색인 기능 구현 자체는 피드 검색 기능 담당 범위입니다.

인프라에서는 OpenSearch가 영구적인 원본 데이터 저장소가 아니라는 기준과
복구 절차만 관리합니다.

현재 운영 환경은 `number_of_replicas=0`인 단일 데이터 노드 구성이므로
Replica Shard를 통한 노드 장애 복구는 제공하지 않습니다.

따라서 Snapshot과 PostgreSQL 원본 데이터 기반 재색인 가능 여부가
복구 정책에서 특히 중요합니다.

---

## 14. 비용 기준

2026-08-19 AWS Pricing API를 통해
서울 리전 `t3.small.search` 가격을 확인했습니다.

- Instance: `t3.small.search`
- 가격: `$0.056 / hour`

단순 30일 기준 인스턴스 비용:

`0.056 × 24 × 30 = 약 $40.32`

여기에 gp3 EBS 10 GiB 등의 비용이 추가됩니다.

현재 프로젝트에서는 비용 절감을 위해
단일 `t3.small.search` 데이터 노드를 사용합니다.

운영 종료 후 불필요한 비용이 발생하지 않도록
OpenSearch Domain을 반드시 정리합니다.

---

## 15. 운영 종료 및 리소스 정리

프로젝트 운영 종료 시 다음 순서로 정리합니다.

### 1. 애플리케이션 연결 제거

ECS Task Definition 또는 서비스 종료를 통해
`SEARCH_ENDPOINT` 사용을 중단합니다.

### 2. 데이터 복구 필요 여부 확인

OpenSearch에만 존재하는 데이터가 없는지 확인하고,
필요한 경우 PostgreSQL 원본 데이터로 재색인이 가능한지 확인합니다.

### 3. OpenSearch Domain 삭제

Domain:

`otboo-prod-search`

삭제 후 Domain이 완전히 제거되었는지 확인합니다.

### 4. OpenSearch Security Group 삭제

Domain 삭제 완료 후 다음 Security Group을 삭제합니다.

`sg-057d137548110f0e1`

Name:

`otboo-prod-opensearch-sg`

Domain이 Security Group을 사용 중인 상태에서
Security Group을 먼저 삭제하지 않습니다.

### 5. Service-Linked Role 확인

`AWSServiceRoleForAmazonOpenSearchService`

다른 OpenSearch Domain에서 사용하고 있는지 확인한 뒤
필요한 경우에만 정리를 검토합니다.

---

## 16. 운영 검증용 임시 리소스 기준

VPC 내부 OpenSearch는 로컬 개발 PC에서 직접 접근할 수 없으므로,
2026-08-25 Replica 설정 확인을 위해
운영 ECS와 동일한 네트워크 조건을 사용하는 일회성 Fargate Task를 사용했습니다.

검증 Task는 다음 조건으로 실행했습니다.

- 운영 ECS와 동일한 VPC 환경
- 운영 ECS와 동일한 Application Security Group
- OpenSearch에 대한 GET 요청만 수행
- 운영 Backend Service와 독립적으로 실행
- 애플리케이션 Backend Container는 실행하지 않음
- 조회 결과는 CloudWatch Logs에 기록

검증을 위해 운영 ECS Service의
`enableExecuteCommand`를 변경하지 않았습니다.

또한 기존 ECS Task Role에
ECS Exec를 위한 `ssmmessages` 권한도 추가하지 않았습니다.

조회용 Task Definition:

`otboo-prod-opensearch-inspect:1`

검증 완료 후 해당 Task Definition은 deregister하여
다음 상태로 정리했습니다.

`INACTIVE`

실행 완료된 일회성 Fargate Task는 모두 종료된 상태이며,
운영 Backend Service의 Task Definition 또는 배포 설정은 변경하지 않았습니다.

---

## 17. 현재 검증 상태

완료:

- Docker Elasticsearch OSS 7.10.2 실행
- 로컬 Elasticsearch Cluster Health 확인
- Elasticsearch REST High Level Client 7.10.2 구성
- Spring Boot 로컬 Client 구성
- 로컬 Elasticsearch 연결 통합 테스트
- 검색 설정의 Connection Timeout 바인딩 테스트
- 검색 설정의 Socket Timeout 바인딩 테스트
- Elasticsearch Client에 Connection Timeout 적용
- Elasticsearch Client에 Socket Timeout 적용
- 지연 응답 상황에서 Socket Timeout 동작 확인
- Connection Timeout 운영 기준 `3초` 확정
- Socket / Response Timeout 운영 기준 `5초` 확정
- AWS OpenSearch Service Domain 생성
- OpenSearch VPC / Subnet / Security Group 구성
- HTTPS / TLS 적용
- 저장 데이터 암호화
- Node-to-node 암호화
- OpenSearch Domain `Active` 확인
- Domain Access Policy `es:*` → `es:ESHttp*` 최소화
- Domain Access Policy 변경 `Active` 적용 확인
- VPC + Security Group 접근 제한 구조 확인
- ECS Task Definition에 `SEARCH_ENDPOINT` 반영
- ECS Revision 45에서 `SEARCH_ENDPOINT` 최초 반영
- 운영 ECS Revision 70에도 `SEARCH_ENDPOINT` 유지 확인
- ECS Deployment `COMPLETED`
- ALB Target Health `healthy` 확인
- 운영 ECS와 동일한 Application Security Group에서 OpenSearch REST API 접근 확인
- 운영 OpenSearch `.kibana_1` 인덱스 `Primary=1`, `Replica=0` 확인
- 운영 OpenSearch `.kibana_1`의 실제 `number_of_replicas=0` 설정 확인
- 단일 데이터 노드의 애플리케이션 인덱스 `number_of_replicas=0` 운영 정책 확정
- Cluster Health `Green` 및 `TotalUnAssignedShards=0` 기준과 Replica 정책 일치 확인
- 운영 검증용 임시 Fargate Task Definition 정리

아직 남은 검증:

- #253 검색 Client Timeout 변경사항이 포함된 운영 이미지 배포
- 실제 운영 Backend 애플리케이션에서 OpenSearch Client 요청 정상 동작 확인
- 피드 애플리케이션 인덱스 생성 시 `number_of_replicas=0` 적용 확인
- 운영 환경 피드 인덱싱 및 검색 기능 확인

피드 인덱스·매핑·CRUD 동기화·재색인·검색 쿼리와
검색 기능 테스트는 피드 검색 기능 담당 범위와 협업하여 진행합니다.