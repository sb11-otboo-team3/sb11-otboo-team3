# Elasticsearch / OpenSearch 운영 구성

## 1. 목적 및 범위

피드 검색 기능의 Full Text Search를 지원하기 위한 검색 인프라를 구성합니다.

개발 환경에서는 Docker 기반 Elasticsearch OSS를 사용하고,
운영 환경에서는 Amazon OpenSearch Service를 사용합니다.

구성 범위는 다음과 같습니다.

- 로컬 Elasticsearch 실행 환경
- Elasticsearch / OpenSearch 호환 버전 및 Client 기준
- AWS OpenSearch Service 운영 환경
- VPC, Subnet, Security Group 구성
- ECS `prod` 환경 연결
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

현재 프로젝트에서는 VPC와 Security Group을 주요 접근 제어 경계로 사용합니다.

운영 기간과 프로젝트 범위를 고려하여
Fine-grained Access Control 및 애플리케이션 SigV4 인증은 현재 구성에 포함하지 않습니다.

실제 장기 운영 서비스에서는 IAM 기반 인증 및 세분화된 접근 제어 적용을 추가 검토해야 합니다.

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

### 현재 ECS Task Definition

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

---

## 8. ECS 배포 기준

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

## 9. 운영 상태 확인

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

---

## 10. 모니터링 및 장애 확인 기준

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
3. Free Storage Space 확인
4. JVM Memory Pressure 확인
5. CPU 사용률 확인
6. Index Write Block 여부 확인
7. ECS Application 로그에서 검색 Client 오류 확인
8. ECS App SG와 OpenSearch SG 규칙 확인

현재 EBS 크기는 10 GiB이므로
Free Storage Space 감소를 특히 주의해서 확인합니다.

---

## 11. 백업 및 복구 기준

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

---

## 12. 비용 기준

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

## 13. 운영 종료 및 리소스 정리

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

## 14. 현재 검증 상태

완료:

- Docker Elasticsearch OSS 7.10.2 실행
- 로컬 Elasticsearch Cluster Health 확인
- Elasticsearch REST High Level Client 7.10.2 구성
- Spring Boot 로컬 Client 구성
- 로컬 Elasticsearch 연결 통합 테스트
- AWS OpenSearch Service Domain 생성
- OpenSearch VPC / Subnet / Security Group 구성
- HTTPS / TLS 적용
- 저장 데이터 암호화
- Node-to-node 암호화
- OpenSearch Domain `Active` 확인
- Cluster Health `Green` 확인
- ECS Task Definition에 `SEARCH_ENDPOINT` 반영
- ECS Revision 45 배포
- ECS Deployment `COMPLETED`
- ALB Target Health `healthy` 확인

아직 남은 검증:

- #206 검색 Client 코드가 포함된 운영 이미지 배포
- ECS 애플리케이션에서 AWS OpenSearch 실제 연결 확인
- 운영 환경 인덱싱·검색 연결 확인

인덱스·매핑·검색 쿼리와 기능 테스트는 피드 검색 기능 담당 범위와 협업하여 진행합니다.