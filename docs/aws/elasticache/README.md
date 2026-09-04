# Amazon ElastiCache for Redis OSS 구성

> **운영 상태 안내**
>
> 이 문서는 Issue #73에서 구성한 Amazon ElastiCache 기반 Redis 운영 환경의
> 설계와 검증 이력을 보존하기 위한 문서입니다.
>
> Issue #279에서는 AWS 운영 비용 최적화를 위해 운영 Redis를
> EC2 통합 데이터 스택의 Redis로 전환합니다.
>
> 전환 후 애플리케이션은 EC2 Private IP의 Redis `6379` 포트에 연결하고,
> 기존 `REDIS_PASSWORD` SSM Secret은 재사용하며
> `REDIS_SSL_ENABLED=false`를 사용합니다.
>
> 기존 ElastiCache는 전환 직후 삭제하지 않고 실제 기능 검증과
> 안정화가 완료될 때까지 Rollback 대상으로 유지합니다.
>
> 현재 운영 데이터 스택은
> [EC2 통합 데이터 스택 운영 구성](../data-stack/README.md)을 기준으로 합니다.


## 1. 구성 목적

운영 환경에서 애플리케이션 캐시와 재생성 가능한 임시 데이터를 관리하기 위해 Amazon ElastiCache for Redis OSS를 구성합니다.

- Redis는 외부 인터넷에 공개하지 않습니다.
- Redis는 ECS Application Security Group에서만 접근할 수 있습니다.
- 전송 중 암호화와 저장 데이터 암호화를 적용합니다.
- Redis AUTH Token은 SSM Parameter Store의 `SecureString`으로 관리합니다.
- 애플리케이션은 TLS를 사용해 Redis에 연결합니다.
- 영구 보관이 필요한 원본 데이터는 Redis에 저장하지 않습니다.

---

## 2. 공통 정보

| 항목 | 값 |
| --- | --- |
| Project | `otboo` |
| Environment | `prod` |
| AWS Region | `ap-northeast-2` |
| ManagedBy | `manual` |
| 구성 이슈 | `#73` |

실제 Redis Endpoint, AUTH Token, AWS 계정 정보 및 인증정보는 Git 저장소에 기록하지 않습니다.

---

## 3. 네트워크 구성

### VPC

| 항목 | 값 |
| --- | --- |
| VPC ID | `vpc-00db8f2d24cb038f9` |
| CIDR | `172.31.0.0/16` |
| 유형 | Default VPC 내부의 별도 Private Subnet 사용 |

### Redis용 Private Subnet

| 이름 | Subnet ID | 가용 영역 | CIDR | Public IP 자동 할당 |
| --- | --- | --- | --- | --- |
| `otboo-prod-db-private-2a` | `subnet-08530421cc2349e5b` | `ap-northeast-2a` | `172.31.64.0/24` | 비활성화 |
| `otboo-prod-db-private-2c` | `subnet-092384d9d96e20d7d` | `ap-northeast-2c` | `172.31.65.0/24` | 비활성화 |

두 Subnet은 RDS와 Redis가 함께 사용하는 운영 데이터 계층용 Private Subnet입니다.

### Route Table

| 항목 | 값 |
| --- | --- |
| Route Table ID | `rtb-0c76bad530924414f` |
| VPC 내부 경로 | `172.31.0.0/16 → local` |
| Internet Gateway 경로 | 없음 |
| NAT Gateway 경로 | 없음 |

두 Subnet에는 인터넷으로 향하는 `0.0.0.0/0` 경로가 없습니다.

### Cache Subnet Group

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-redis-subnet-group` |
| VPC | `vpc-00db8f2d24cb038f9` |
| 가용 영역 | `ap-northeast-2a`, `ap-northeast-2c` |
| Public Subnet 포함 | 없음 |

---

## 4. Security Group 구성

### ECS Application Security Group

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-app-sg` |
| Security Group ID | `sg-0721e7157c9051e76` |
| 용도 | ECS 애플리케이션 Task |

### Redis Security Group

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-redis-sg` |
| Security Group ID | `sg-0e8ef7fa113cc468d` |
| 허용 프로토콜 | TCP |
| 허용 포트 | `6379` |
| 허용 소스 | `otboo-prod-app-sg` |
| CIDR 기반 인바운드 | 없음 |

```text
otboo-prod-app-sg
        │
        │ TCP 6379
        ▼
otboo-prod-redis-sg
```

다음 대상은 Redis 인바운드에 허용하지 않습니다.

```text
0.0.0.0/0
개인 공인 IP
172.31.0.0/16
Default Security Group
```

Security Group의 아웃바운드 `0.0.0.0/0` 규칙은 인바운드 인터넷 공개를 의미하지 않습니다.

---

## 5. ElastiCache Redis OSS 구성

| 항목 | 값 |
| --- | --- |
| Replication Group ID | `otboo-prod-redis` |
| Engine | Redis OSS |
| Engine Version | `7.1.0` |
| Node Type | `cache.t4g.micro` |
| Primary Node | 1개 |
| Replica | 0개 |
| Cluster Mode | 비활성화 |
| Multi-AZ | 비활성화 |
| Automatic Failover | 비활성화 |
| Port | `6379` |
| Cache Subnet Group | `otboo-prod-redis-subnet-group` |
| Security Group | `otboo-prod-redis-sg` |
| Transit Encryption | 활성화 |
| Transit Encryption Mode | `required` |
| At-Rest Encryption | 활성화 |
| AUTH Token | 활성화 |
| Snapshot Retention | 0일 |
| Auto Minor Version Upgrade | 활성화 |
| Maintenance Window | `sun:18:00-sun:19:00` (UTC, 월요일 03:00~04:00 KST) |
| 상태 확인 결과 | `available` |

현재 구성은 프로젝트 운영 비용을 줄이기 위한 단일 노드 구조입니다.

Replica가 없기 때문에 노드 장애 또는 유지보수 중 Redis를 일시적으로 사용할 수 없을 수 있습니다.

애플리케이션은 Redis 장애가 핵심 데이터 유실로 이어지지 않도록 캐시와 재생성 가능한 데이터에만 Redis를 사용합니다.

---

## 6. 인증정보 관리

Redis AUTH Token은 다음 SSM Parameter Store 경로에 저장합니다.

```text
/otboo/prod/redis/auth-token
```

| 항목 | 값 |
| --- | --- |
| Parameter Type | `SecureString` |
| Environment | `prod` |
| ECS 주입 환경변수 | `REDIS_PASSWORD` |

실제 AUTH Token 값은 다음 위치에 기록하지 않습니다.

```text
GitHub 저장소
Issue
Pull Request
README
팀 채널
애플리케이션 YAML
Dockerfile
Docker 이미지
```

SSM Parameter의 존재 여부는 실제 값을 복호화하지 않고 확인합니다.

```bash
aws ssm get-parameter \
  --name "/otboo/prod/redis/auth-token" \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'Parameter.{
    Name:Name,
    Type:Type,
    Version:Version
  }' \
  --output table
```

확인 시 `--with-decryption` 옵션은 사용하지 않습니다.

---

## 7. 운영 Redis 환경변수

운영 환경에서 사용하는 Redis 환경변수는 다음과 같습니다.

```text
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
REDIS_SSL_ENABLED
```

`.env.prod.example`의 Redis 설정 구조:

```dotenv
# Redis
REDIS_HOST=
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_SSL_ENABLED=true
```

실제 운영 환경에서는 다음 기준으로 값을 주입합니다.

| 환경변수 | 관리 방식 |
| --- | --- |
| `REDIS_HOST` | ECS 일반 환경변수 |
| `REDIS_PORT` | ECS 일반 환경변수 |
| `REDIS_SSL_ENABLED` | ECS 일반 환경변수 |
| `REDIS_PASSWORD` | SSM Parameter Store Secret |

`REDIS_HOST`에는 ElastiCache Primary Endpoint를 입력하지만 실제 Endpoint는 Git 저장소에 기록하지 않습니다.

`REDIS_PASSWORD`에는 `/otboo/prod/redis/auth-token`의 값을 ECS Secret으로 주입합니다.

---

## 8. Spring Boot 운영 설정

`src/main/resources/application-prod.yaml`에서 다음 설정을 사용합니다.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:true}
```

ElastiCache의 전송 중 암호화 모드가 `required`이므로 운영 환경의 `REDIS_SSL_ENABLED`는 `true`를 사용합니다.

로컬 Docker Redis는 TLS를 적용하지 않으므로 로컬 환경 설정과 운영 환경 설정을 구분합니다.

---

## 9. ECS 연동 구조

ECS Task Definition에는 다음 값을 연결합니다.

### 일반 환경변수

```text
REDIS_HOST=<ElastiCache Primary Endpoint>
REDIS_PORT=6379
REDIS_SSL_ENABLED=true
```

### Secret

```text
REDIS_PASSWORD=/otboo/prod/redis/auth-token
```

SSM Parameter를 ECS Task에 Secret으로 주입하는 권한은 ECS Task Execution Role에 설정합니다.

사람용 IAM 사용자의 인증정보를 ECS Task 또는 애플리케이션에서 사용하지 않습니다.

실제 ECS Task Definition 연결과 Redis 접속 검증은 ECS 구성 이슈에서 진행합니다.

---

## 10. 구성 검증

### Replication Group

```bash
aws elasticache describe-replication-groups \
  --replication-group-id otboo-prod-redis \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'ReplicationGroups[0].{
    Status:Status,
    ClusterMode:ClusterEnabled,
    AutomaticFailover:AutomaticFailover,
    MultiAZ:MultiAZ,
    NodeType:CacheNodeType,
    TransitEncryption:TransitEncryptionEnabled,
    TransitEncryptionMode:TransitEncryptionMode,
    AtRestEncryption:AtRestEncryptionEnabled,
    AuthTokenEnabled:AuthTokenEnabled,
    SnapshotRetentionDays:SnapshotRetentionLimit,
    AutoMinorUpgrade:AutoMinorVersionUpgrade,
    Port:NodeGroups[0].PrimaryEndpoint.Port
  }' \
  --output json
```

정상 기준:

```text
Status                  available
ClusterMode             false
AutomaticFailover       disabled
MultiAZ                  disabled
NodeType                 cache.t4g.micro
TransitEncryption        true
TransitEncryptionMode    required
AtRestEncryption         true
AuthTokenEnabled         true
SnapshotRetentionDays   0
AutoMinorUpgrade         true
Port                     6379
```

Primary Endpoint는 검증할 수 있지만 출력값을 Issue나 README에 그대로 공유하지 않습니다.

### Cache Cluster

```bash
aws elasticache describe-cache-clusters \
  --cache-cluster-id otboo-prod-redis-001 \
  --show-cache-node-info \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'CacheClusters[0].{
    Status:CacheClusterStatus,
    Engine:Engine,
    EngineVersion:EngineVersion,
    NodeType:CacheNodeType,
    NodeCount:NumCacheNodes,
    SubnetGroup:CacheSubnetGroupName,
    SecurityGroup:SecurityGroups[0].SecurityGroupId,
    SecurityGroupStatus:SecurityGroups[0].Status,
    MaintenanceWindow:PreferredMaintenanceWindow,
    AutoMinorUpgrade:AutoMinorVersionUpgrade
  }' \
  --output json
```

정상 기준:

```text
Status               available
Engine               redis
EngineVersion        7.1.0
NodeType             cache.t4g.micro
NodeCount            1
SubnetGroup          otboo-prod-redis-subnet-group
SecurityGroup        sg-0e8ef7fa113cc468d
SecurityGroupStatus  active
MaintenanceWindow    sun:18:00-sun:19:00
AutoMinorUpgrade     true
```
`MaintenanceWindow`의 `sun:18:00-sun:19:00`은 UTC 기준이며, 한국 시간으로는 월요일 03:00~04:00입니다.

### Cache Subnet Group

```bash
aws elasticache describe-cache-subnet-groups \
  --cache-subnet-group-name otboo-prod-redis-subnet-group \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'CacheSubnetGroups[0].{
    Name:CacheSubnetGroupName,
    VpcId:VpcId,
    Subnets:Subnets[].{
      SubnetId:SubnetIdentifier,
      AZ:SubnetAvailabilityZone.Name
    }
  }' \
  --output json
```

### Redis Security Group

```bash
aws ec2 describe-security-groups \
  --group-ids sg-0e8ef7fa113cc468d \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'SecurityGroups[0].{
    Name:GroupName,
    VpcId:VpcId,
    Inbound:IpPermissions,
    Outbound:IpPermissionsEgress
  }' \
  --output json
```

Redis 인바운드에는 TCP `6379`와 ECS Application Security Group만 존재해야 합니다.

---

## 11. 태그

### ElastiCache

```text
Project=otboo
Environment=prod
Owner=team3
ManagedBy=manual
Purpose=application-cache
```

### Redis Security Group

```text
Name=otboo-prod-redis-sg
Project=otboo
Environment=prod
Owner=team3
ManagedBy=manual
Purpose=redis-access
```

### SSM Parameter

```text
Project=otboo
Environment=prod
Owner=team3
ManagedBy=manual
Purpose=redis-auth
```

태그 키는 대소문자와 철자를 구분하므로 `Environment` 표기를 동일하게 사용합니다.

태그에는 Endpoint, 비밀번호, AUTH Token 등의 민감정보를 작성하지 않습니다.

---

## 12. 비용

서울 리전 `cache.t4g.micro` Redis OSS 단일 노드의 On-Demand 가격을 기준으로 계산했습니다.

| 기준 | 예상 비용 |
| --- | --- |
| 시간당 | `$0.024` |
| 하루 24시간 | 약 `$0.58` |
| 30일 720시간 | 약 `$17.28` |
| 월 730시간 | 약 `$17.52` |
| 6주 1,008시간 | 약 `$24.19` |

위 금액은 Redis 노드 비용만 계산한 예상값입니다.

다음 항목에 따라 실제 청구액이 달라질 수 있습니다.

```text
실제 사용 시간
세금
데이터 전송
엔진 지원 정책 변경
AWS 가격 변경
추가 노드 또는 Replica 생성
```

ElastiCache는 사용하지 않는 동안 정지해 둘 수 있는 리소스로 관리하지 않습니다.

프로젝트 종료 후 더 이상 사용하지 않는 경우 Replication Group을 삭제해야 노드 비용 발생을 중단할 수 있습니다.

---

## 13. 후속 연동

다음 작업은 ECS 구성 이슈에서 진행합니다.

- ECS Task Definition에 `REDIS_HOST` 추가
- ECS Task Definition에 `REDIS_PORT=6379` 추가
- ECS Task Definition에 `REDIS_SSL_ENABLED=true` 추가
- `/otboo/prod/redis/auth-token`을 `REDIS_PASSWORD` Secret으로 연결
- ECS Task Execution Role에 SSM Parameter 조회 권한 추가
- ECS Task에서 TLS Redis 연결 검증
- 캐시 저장 및 조회 동작 검증
- Redis 장애 시 애플리케이션 동작 확인

로컬 Mac은 Redis Security Group의 허용 대상이 아니므로 운영 Redis에 직접 접속하지 않습니다.

---

## 14. 운영 종료 전 확인

Redis 삭제 전 다음 사항을 확인합니다.

- ECS Service와 Task Definition에서 Redis를 사용 중이지 않은지 확인
- 배치 또는 스케줄러가 Redis를 사용 중이지 않은지 확인
- Redis에 영구 보존이 필요한 데이터가 없는지 확인
- 팀원에게 Redis 종료 일정을 공유
- 삭제 대상 AWS 계정과 리전 확인
- AWS CLI Profile이 `otboo`인지 확인
- Replication Group ID가 `otboo-prod-redis`인지 확인

계정과 리전을 확인합니다.

```bash
aws sts get-caller-identity \
  --profile otboo

aws configure get region \
  --profile otboo
```

명령 출력의 AWS 계정 ID와 사용자 ARN은 Issue, PR 또는 README에 공유하지 않습니다.

---

## 15. 운영 종료 및 삭제 절차

> 아래 명령은 프로젝트 운영이 완전히 종료된 후에만 실행합니다.

### 1단계: Replication Group 상태 확인

```bash
aws elasticache describe-replication-groups \
  --replication-group-id otboo-prod-redis \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'ReplicationGroups[0].{
    Id:ReplicationGroupId,
    Status:Status,
    NodeType:CacheNodeType,
    SnapshotRetentionDays:SnapshotRetentionLimit
  }' \
  --output table
```

삭제 전 상태가 `available`인지 확인합니다.

### 2단계: Replication Group 삭제

현재 Redis는 캐시와 재생성 가능한 임시 데이터만 저장하므로 운영 종료 시 최종 Snapshot을 생성하지 않는 것을 기본 정책으로 합니다.

영구 보관이 필요한 데이터가 발견되면 삭제를 중단하고 별도의 보존 방법을 먼저 결정합니다.

```bash
aws elasticache delete-replication-group \
  --replication-group-id otboo-prod-redis \
  --no-retain-primary-cluster \
  --region ap-northeast-2 \
  --profile otboo
```

삭제가 완료될 때까지 기다립니다.

```bash
aws elasticache wait replication-group-deleted \
  --replication-group-id otboo-prod-redis \
  --region ap-northeast-2 \
  --profile otboo
```

### 3단계: Replication Group 삭제 확인

```bash
aws elasticache describe-replication-groups \
  --replication-group-id otboo-prod-redis \
  --region ap-northeast-2 \
  --profile otboo
```

`ReplicationGroupNotFoundFault`가 반환되면 삭제가 완료된 것입니다.

### 4단계: Cache Subnet Group 삭제

Replication Group 삭제가 완전히 끝난 후 실행합니다.

```bash
aws elasticache delete-cache-subnet-group \
  --cache-subnet-group-name otboo-prod-redis-subnet-group \
  --region ap-northeast-2 \
  --profile otboo
```

삭제 여부를 확인합니다.

```bash
aws elasticache describe-cache-subnet-groups \
  --cache-subnet-group-name otboo-prod-redis-subnet-group \
  --region ap-northeast-2 \
  --profile otboo
```

`CacheSubnetGroupNotFoundFault`가 반환되면 삭제가 완료된 것입니다.

RDS도 같은 Private Subnet을 사용하므로 실제 EC2 Subnet과 Route Table은 Redis 종료 과정에서 삭제하지 않습니다.

### 5단계: Redis Security Group 삭제

Redis Security Group이 다른 리소스에 연결돼 있지 않은지 확인합니다.

```bash
aws ec2 describe-network-interfaces \
  --filters Name=group-id,Values=sg-0e8ef7fa113cc468d \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'NetworkInterfaces[].{
    NetworkInterfaceId:NetworkInterfaceId,
    Status:Status,
    Description:Description
  }' \
  --output table
```

조회 결과가 비어 있으면 Redis Security Group을 삭제합니다.

```bash
aws ec2 delete-security-group \
  --group-id sg-0e8ef7fa113cc468d \
  --region ap-northeast-2 \
  --profile otboo
```

ECS Application Security Group은 ECS에서 계속 사용하므로 삭제하지 않습니다.

### 6단계: SSM AUTH Token 삭제

ECS Task Definition 또는 다른 리소스에서 Parameter를 더 이상 참조하지 않는지 먼저 확인합니다.

참조가 완전히 제거된 경우에만 삭제합니다.

```bash
aws ssm delete-parameter \
  --name "/otboo/prod/redis/auth-token" \
  --region ap-northeast-2 \
  --profile otboo
```

삭제 여부를 확인합니다.

```bash
aws ssm get-parameter \
  --name "/otboo/prod/redis/auth-token" \
  --region ap-northeast-2 \
  --profile otboo
```

`ParameterNotFound`가 반환되면 삭제가 완료된 것입니다.

### 7단계: 최종 잔존 리소스 확인

```bash
aws ec2 describe-security-groups \
  --filters \
    Name=group-name,Values=otboo-prod-redis-sg \
    Name=vpc-id,Values=vpc-00db8f2d24cb038f9 \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'SecurityGroups[].{
    Name:GroupName,
    GroupId:GroupId
  }' \
  --output table
```

```bash
aws elasticache describe-cache-subnet-groups \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'CacheSubnetGroups[?CacheSubnetGroupName==`otboo-prod-redis-subnet-group`].CacheSubnetGroupName' \
  --output table
```

```bash
aws ec2 describe-security-groups \
  --filters Name=group-name,Values=otboo-prod-redis-sg \
  --region ap-northeast-2 \
  --profile otboo \
  --query 'SecurityGroups[].{
    Name:GroupName,
    GroupId:GroupId
  }' \
  --output table
```

다음 세 리소스가 조회되지 않아야 합니다.

```text
otboo-prod-redis
otboo-prod-redis-subnet-group
otboo-prod-redis-sg
```

SSM Parameter도 삭제하기로 결정한 경우 `/otboo/prod/redis/auth-token`이 조회되지 않아야 합니다.

마지막으로 Billing 또는 Cost Explorer에서 ElastiCache 비용 발생 여부를 확인합니다.

---

## 16. 삭제하지 않는 공용 리소스

Redis 운영 종료 시 다음 리소스는 Redis와 별도로 사용되므로 함께 삭제하지 않습니다.

```text
vpc-00db8f2d24cb038f9
subnet-08530421cc2349e5b
subnet-092384d9d96e20d7d
rtb-0c76bad530924414f
sg-0721e7157c9051e76
```

해당 리소스는 RDS 또는 ECS에서도 사용하므로 각 서비스의 종료 계획에 따라 별도로 관리합니다.