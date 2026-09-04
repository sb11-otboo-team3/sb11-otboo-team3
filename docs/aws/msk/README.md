# Amazon MSK Provisioned 운영 Kafka 구성

> **운영 상태 안내**
>
> 이 문서는 Issue #193에서 구성한 Amazon MSK Provisioned 기반
> 운영 Kafka 환경의 설계와 검증 이력을 보존하기 위한 문서입니다.
>
> Issue #279에서는 AWS 운영 비용 최적화를 위해 운영 Kafka를
> EC2 통합 데이터 스택의 단일 Kafka Broker로 전환합니다.
>
> 전환 후 애플리케이션은 EC2 Private IP의 Kafka `9092` 포트에 연결하고,
> Kafka 연결 프로토콜은 다음과 같이 변경합니다.
>
> ```text
> 기존 MSK
> SASL_SSL + AWS_MSK_IAM
>
> Issue #279 EC2 Kafka
> PLAINTEXT + VPC / Security Group
> ```
>
> EC2 Kafka는 `apache/kafka:3.9.2` 기반 KRaft 단일 노드로 운영하며,
> Replication Factor는 `1`을 사용합니다.
>
> 기존 Amazon MSK는 전환 직후 삭제하지 않고
> Consumer Lag과 Notification Outbox 상태 확인,
> 실제 Produce / Consume 검증 및 안정화가 완료될 때까지
> Rollback 대상으로 유지합니다.
>
> 현재 운영 데이터 스택 기준은
> [EC2 통합 데이터 스택 운영 구성](../data-stack/README.md)을 참고합니다.


## 1. 구성 목적

운영 환경에서 Kafka 기반 비동기 메시징을 사용할 수 있도록
Amazon MSK Provisioned Cluster를 구성하고,
ECS 애플리케이션이 IAM 인증을 통해 안전하게 Kafka에 접근하도록 구성합니다.

Kafka Topic, Consumer Group, Retry, DLT 등의 애플리케이션 공통 규칙은
`docs/kafka/README.md`에서 관리합니다.

이 문서에서는 다음 AWS 운영 구성을 다룹니다.

- Amazon MSK Provisioned Cluster
- Private Subnet 및 Security Group
- ECS Task Role 기반 IAM 인증
- Spring Boot 운영 Kafka 연결
- ECS 환경에서 실제 Produce·Consume 검증
- CloudWatch 모니터링
- 비용 관리
- 운영 종료 및 삭제 절차

---

## 2. 관련 이슈 및 문서

- Issue #191: Kafka 공통 메시징 환경 구성
- Issue #193: Amazon MSK 운영 Kafka 환경 구성
- [Kafka 공통 메시징 기준](../../kafka/README.md)
- [AWS 기본 운영 기준](../README.md)
- [ECS Fargate 및 ALB 구성](../ecs-alb/README.md)
- [Amazon ECR 구성 및 이미지 검증](../ecr/README.md)

---

## 3. 최종 운영 구성

운영 Kafka는 다음 구성으로 생성했습니다.

```text
Service: Amazon MSK Provisioned
Broker Type: Standard
Kafka Version: 3.9.x
Metadata Mode: ZooKeeper

Broker Instance: kafka.t3.small
Availability Zones: 2
Brokers per AZ: 1
Total Brokers: 2

Storage: EBS
Storage per Broker: 10 GiB

Authentication: IAM
Client Encryption: TLS
Inter-Broker Encryption: TLS
Public Access: DISABLED

Monitoring: DEFAULT
Storage Mode: LOCAL
```

운영 Kafka는 외부 인터넷에 공개하지 않으며
ECS 애플리케이션과 동일한 VPC 내부에서 연결합니다.

---

## 4. Kafka Metadata Mode 선택

### 최초 검토 구성

최초에는 다음 구성을 검토했습니다.

```text
Kafka 3.9.x
KRaft
kafka.t3.small × 2
```

그러나 실제 Amazon MSK Cluster 생성 과정에서
KRaft 버전과 `kafka.t3.small` 조합으로 생성 요청 시
지원되지 않는 Instance Type 오류를 확인했습니다.

KRaft에서 사용할 수 있는 더 큰 Broker Instance도 검토했으나,
현재 프로젝트는 학습 및 제한된 운영 기간을 전제로 하고 있어
Broker 비용 증가 폭이 큰 구성을 선택하지 않았습니다.

따라서 최종적으로 다음 구성을 선택했습니다.

```text
Kafka 3.9.x
ZooKeeper
kafka.t3.small × 2
```

현재 Cluster는 ZooKeeper Metadata Mode로 생성되어 있으며,
운영 중 Metadata Mode를 KRaft로 직접 전환하는 것을 전제로 하지 않습니다.

향후 KRaft가 필요한 경우 별도 KRaft Cluster 생성과
데이터 이전 전략을 검토합니다.

---

## 5. 네트워크 구성

### VPC

Amazon MSK는 ECS 애플리케이션, RDS, Redis와 동일한 VPC를 사용합니다.

MSK Broker는 Public Internet에 직접 노출하지 않습니다.

```text
Internet
    │
    ▼
ALB
    │
    ▼
ECS Task
    │
    │ TCP 9098 / IAM
    ▼
Amazon MSK
Private Subnet
```

ECS Task가 MSK Broker와 같은 VPC 내부에서 통신하며,
MSK Security Group은 ECS Application Security Group에서 들어오는
Kafka IAM 연결만 허용합니다.

### Private Subnet

기존 운영 DB에 사용 중인 Private Subnet 2개를
MSK Broker에도 재사용합니다.

```text
otboo-prod-db-private-2a
otboo-prod-db-private-2c
```

Subnet 이름에는 `db`가 포함되어 있지만
특정 RDS Instance에 종속된 Subnet이 아니며,
동일 VPC의 일반 Private Subnet이므로 MSK에서도 재사용합니다.

MSK 전용 Subnet을 추가로 생성하지 않아
불필요한 네트워크 리소스 증가를 피합니다.

Broker는 서로 다른 Availability Zone에 배치합니다.

```text
AZ 1
└── Broker 1

AZ 2
└── Broker 2
```

---

## 6. Security Group 구성

### ECS Application Security Group

ECS 애플리케이션은 기존 Security Group을 그대로 사용합니다.

```text
otboo-prod-app-sg
```

### MSK Security Group

MSK 전용 Security Group은 다음 이름을 사용합니다.

```text
otboo-prod-msk-sg
```

Inbound는 다음 규칙만 허용합니다.

```text
Protocol: TCP
Port: 9098
Source: otboo-prod-app-sg
```

Public CIDR인 다음 형태의 접근은 허용하지 않습니다.

```text
0.0.0.0/0
```

따라서 Kafka Broker는 인터넷에서 직접 접근할 수 없습니다.

IAM 인증을 사용하는 MSK Broker에
AWS 내부 클라이언트가 연결할 때 사용하는 포트는 `9098`입니다.

---

## 7. IAM 인증 구조

MSK Client 인증에는 별도 Kafka 사용자 이름과 비밀번호를 사용하지 않습니다.

ECS Task가 가지고 있는 Task Role을 통해 IAM 인증을 수행합니다.

```text
ECS Task
    │
    ▼
ECS Task Role
    │
    ▼
Amazon MSK IAM Authentication
    │
    ▼
Kafka Broker
```

사용하는 Task Role:

```text
otboo-prod-ecs-task-role
```

MSK Client 정책:

```text
otboo-prod-msk-client-policy
```

Kafka 권한은 ECS Task Execution Role이 아니라
애플리케이션이 실제로 사용하는 ECS Task Role에 부여합니다.

---

## 8. ECS Task Role 최소 권한

애플리케이션에는 Kafka 관리 전체 권한을 제공하지 않습니다.

### Cluster 권한

다음 권한만 허용합니다.

```text
kafka-cluster:Connect
kafka-cluster:WriteDataIdempotently
```

`WriteDataIdempotently`는 Cluster 수준 권한으로 관리합니다.

### Topic 권한

애플리케이션 Topic은 다음 Prefix만 허용합니다.

```text
otboo.*
```

허용 권한:

```text
kafka-cluster:DescribeTopic
kafka-cluster:WriteData
kafka-cluster:ReadData
```

### Consumer Group 권한

Consumer Group은 다음 Prefix만 허용합니다.

```text
otboo-*
```

허용 권한:

```text
kafka-cluster:DescribeGroup
kafka-cluster:AlterGroup
```

### 허용하지 않는 권한

ECS 애플리케이션에는 다음과 같은 Kafka 관리 권한을 부여하지 않습니다.

```text
CreateTopic
DeleteTopic
AlterTopic
kafka-cluster:*
```

Topic 생성과 삭제 같은 운영 관리 작업은
애플리케이션 Task Role이 아닌 운영자 권한으로 수행합니다.

Kafka Transaction은 현재 사용하지 않으므로
Transactional ID 관련 권한도 추가하지 않습니다.

---

## 9. Topic 관리

Kafka Topic은 애플리케이션 실행 과정에서 자동 생성하는 것을 기본으로 하지 않습니다.

Topic 관리 작업은 운영자 권한으로 별도로 수행합니다.

Kafka Topic 명명 규칙은 다음 형식을 따릅니다.

```text
otboo.<domain>.<event>.v<version>
```

자세한 규칙은 다음 문서를 따릅니다.

```text
docs/kafka/README.md
```

### 운영 연결 검증 Topic

MSK 연결 검증에는 다음 Topic을 사용합니다.

```text
otboo.infrastructure.connectivity-checked.v1
```

구성:

```text
Partition Count: 1
Replication Factor: 2
```

Smoke Test를 실행하기 전에 운영자 권한으로
해당 Topic이 존재하며 기대한 구성인지 확인합니다.

```bash
aws kafka describe-topic \
  --cluster-arn "$MSK_CLUSTER_ARN" \
  --topic-name otboo.infrastructure.connectivity-checked.v1 \
  --profile otboo \
  --region ap-northeast-2 \
  --query '{
    TopicName:TopicName,
    Status:Status,
    PartitionCount:PartitionCount,
    ReplicationFactor:ReplicationFactor
  }' \
  --output table
```

다음 상태를 확인한 후 one-off ECS Task를 실행합니다.

```text
TopicName: otboo.infrastructure.connectivity-checked.v1
Status: ACTIVE
PartitionCount: 1
ReplicationFactor: 2
```

Topic이 존재하지 않거나 설정이 다르면
Smoke Task를 실행하지 않고 운영자 권한으로 Topic 구성을 먼저 수정합니다.

애플리케이션 Task Role에는 `CreateTopic` 권한을 제공하지 않으므로
Topic 생성 및 수정은 운영자 권한으로 수행합니다.

이 Topic은 특정 비즈니스 도메인에 종속되지 않고
인프라 연결 상태를 확인하기 위한 목적으로 사용합니다.

---

## 10. Spring Boot IAM 연결 설정

Amazon MSK IAM 인증을 위해 다음 라이브러리를 사용합니다.

```gradle
implementation 'software.amazon.msk:aws-msk-iam-auth:2.3.6'
```

운영 환경에서는 다음 Kafka 설정을 사용합니다.

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    security:
      protocol: SASL_SSL
    properties:
      "[sasl.mechanism]": AWS_MSK_IAM
      "[sasl.jaas.config]": "software.amazon.msk.auth.iam.IAMLoginModule required;"
      "[sasl.client.callback.handler.class]": software.amazon.msk.auth.iam.IAMClientCallbackHandler
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      enable-auto-commit: false
      auto-offset-reset: earliest
```

운영 ECS에서는 다음과 같은 별도 AWS 인증정보를 설정하지 않습니다.

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
awsProfileName
Kafka Username
Kafka Password
```

애플리케이션은 ECS Task Role에서 제공되는 자격증명을 이용해
MSK IAM 인증을 수행합니다.

Consumer Group은 각 Consumer의 목적에 따라 결정하므로
운영 공통 설정에 전역 `group-id`를 지정하지 않습니다.

---

## 11. 운영 Bootstrap Server

IAM 인증용 MSK Bootstrap Server는 ECS Task Definition의
일반 환경변수로 주입합니다.

```text
KAFKA_BOOTSTRAP_SERVERS
```

Broker Endpoint를 다음 위치에 직접 작성하지 않습니다.

```text
소스 코드
README
Issue
PR
GitHub Actions Workflow
```

Bootstrap Server 주소는 인증 비밀번호는 아니지만
운영 인프라 식별정보이므로 공개 저장소에 직접 기록하지 않습니다.

IAM 인증 자체에는 별도의 Kafka Password나 Secret이 필요하지 않습니다.

---

## 12. ECS Task Definition 연동

운영 Backend Task Definition에 다음 환경변수를 추가합니다.

```text
KAFKA_BOOTSTRAP_SERVERS
```

기존 ECS Service가 사용하는 Task Definition을 기반으로
새 Revision을 등록한 뒤 Service에 적용했습니다.

Task Definition 변경 후 다음 상태를 확인합니다.

```text
Desired: 1
Running: 1
Pending: 0
Deployments: 1
```

ALB Target도 최종적으로 다음 상태인지 확인합니다.

```text
healthy
```

기존 Task가 교체되는 동안 일시적으로 다음 상태가 함께 나타날 수 있습니다.

```text
draining
healthy
```

이 경우 기존 Target의 Deregistration이 끝난 뒤
`healthy` Target만 남는 것을 확인합니다.

---

## 13. 운영 Produce·Consume Smoke Test

운영 환경에서 단순히 Cluster가 `ACTIVE`인지 확인하는 것만으로
Kafka 연결이 완료되었다고 판단하지 않습니다.

실제 ECS Task에서 다음 전체 흐름을 검증합니다.

```text
ECS Task
    │
    ▼
ECS Task Role
    │
    ▼
AWS_MSK_IAM
    │
    ▼
MSK Broker :9098
    │
    ▼
Produce
    │
    ▼
otboo.infrastructure.connectivity-checked.v1
    │
    ▼
Consume
```

### Smoke Probe

운영 연결 검증용 Probe는 기본적으로 비활성화합니다.

```text
KAFKA_SMOKE_ENABLED=false
```

실제 검증용 one-off ECS Task에서만 다음 값으로 Override합니다.

```text
KAFKA_SMOKE_ENABLED=true
```

검증용 Consumer Group은 기존 명명 규칙에 맞게
다음 Prefix를 사용합니다.

```text
otboo-infrastructure-smoke-consumer-
```

운영 ECS Service 자체에는 Smoke Test를 활성화하지 않습니다.

---

## 14. Smoke Test 실행 방식

검증에서는 운영 ECS Service를 직접 변경하지 않고
별도의 one-off Fargate Task를 실행합니다.

```text
운영 Task Definition
        │
        ▼
Smoke 전용 Task Definition 복제
        │
        ├── Backend Image → Smoke 검증 이미지
        └── KAFKA_SMOKE_ENABLED=true
        │
        ▼
ECS run-task
```

Smoke 전용 Task Definition Family:

```text
otboo-prod-msk-smoke
```

이를 통해 기존 운영 Service의 다음 값에는 영향을 주지 않습니다.

```text
Desired Count
Running Task
ALB Target
운영 Backend Image
```

---

## 15. 실제 검증 결과

운영 ECS one-off Task에서 다음 동작을 실제로 확인했습니다.

```text
1. Spring Boot prod Profile 기동
2. ECS Task Role 획득
3. AWS_MSK_IAM 인증
4. MSK Broker 연결
5. 검증 Topic Produce
6. 동일 메시지 Consume
7. CloudWatch 성공 로그 확인
```

성공 로그 형식:

```text
Kafka MSK smoke test success.
topic=otboo.infrastructure.connectivity-checked.v1,
partition=0,
offset=...
```

실제 검증에서 Produce한 메시지가
동일 Topic의 Consumer에 정상적으로 전달되는 것을 확인했습니다.

따라서 다음 항목을 함께 검증한 것으로 판단합니다.

```text
ECS → MSK 네트워크 경로
Security Group TCP 9098
TLS 연결
ECS Task Role 자격증명
MSK IAM 인증
Topic Describe
Topic Write
Topic Read
Consumer Group 접근
실제 Produce
실제 Consume
```

Smoke 검증이 완료된 후 one-off Task를 종료했습니다.

---

## 16. Smoke Test 리소스 관리

Smoke Test를 위해 생성한 임시 AWS 리소스는
검증 완료 후 운영 리소스와 분리하여 정리합니다.

정리 대상:

```text
Smoke one-off ECS Task
Smoke 전용 Task Definition
Smoke 전용 ECR Image
```

Smoke Task Definition은 Deregister하여
새로운 Task를 실행할 수 없도록 처리합니다.

Smoke ECR Image도 운영 Service에서 사용하지 않는 것을 확인한 뒤 삭제할 수 있습니다.

반면 다음 항목은 운영 Kafka 구성에 포함되므로 유지합니다.

```text
Amazon MSK Cluster
MSK Security Group
MSK IAM Client Policy
KAFKA_BOOTSTRAP_SERVERS
Connectivity Check Topic
Smoke Probe 코드
```

Smoke Probe 코드는 기본 비활성 상태로 유지하여
향후 네트워크 또는 IAM 설정 변경 후 재검증에 사용할 수 있도록 합니다.

---

## 17. CloudWatch 모니터링

Amazon MSK는 CloudWatch와 연동하여
Broker 및 Kafka 상태 지표를 수집합니다.

현재 Monitoring Level은 다음과 같습니다.

```text
DEFAULT
```

프로젝트 규모와 운영 기간을 고려하여
추가 비용이 발생할 수 있는 세부 Monitoring Level을 기본 활성화하지 않습니다.

운영 중 우선 확인할 지표는 다음과 같습니다.

### Partition 상태

```text
OfflinePartitionsCount
UnderReplicatedPartitions
UnderMinIsrPartitionCount
```

정상 운영 시 Offline Partition이나
Under Replicated Partition이 지속적으로 발생하지 않는지 확인합니다.

### Broker 자원

```text
CpuUser
CpuSystem
CpuIdle
CPUCreditBalance
KafkaDataLogsDiskUsed
```

현재 `kafka.t3.small`을 사용하므로
CPU 사용률뿐 아니라 CPU Credit 상태도 함께 확인합니다.

### Consumer Lag

실제 도메인 Consumer를 적용한 뒤에는 다음 지표도 확인합니다.

```text
MaxOffsetLag
SumOffsetLag
RollingEstimatedTimeLagMax
```

Consumer Lag이 계속 증가하는 경우
Consumer 처리량, Partition 수, Broker 부하를 함께 확인합니다.

### ZooKeeper

현재 Cluster가 ZooKeeper Metadata Mode이므로
다음 지표도 확인할 수 있습니다.

```text
ZooKeeperSessionState
ZooKeeperRequestLatencyMsMean
```

---

## 18. 비용 관리

Amazon MSK Provisioned Standard는 주로 다음 항목에서 비용이 발생합니다.

```text
Broker Instance 사용 시간
+
Provisioned EBS Storage
+
필요한 경우 추가 데이터 전송 및 부가 기능
```

현재 비용 절감을 위해 다음 사양을 사용합니다.

```text
kafka.t3.small × 2
EBS 10 GiB × 2
Monitoring DEFAULT
```

대략적인 비용 구조는 다음과 같이 계산합니다.

```text
Broker 비용
= kafka.t3.small 시간당 단가
× Broker 2대
× 운영 시간

Storage 비용
= Broker당 10 GiB
× Broker 2대
× 해당 Region의 Storage 단가
```

AWS 서비스 가격은 Region과 시점에 따라 변경될 수 있으므로
README에 고정된 금액을 운영 기준으로 사용하지 않습니다.

실제 운영 전에는 AWS Pricing 페이지 또는
AWS Pricing Calculator에서 현재 Region의 요금을 다시 확인합니다.

MSK Provisioned Cluster는 사용하지 않더라도
Cluster를 유지하는 동안 Broker 비용이 발생할 수 있으므로
프로젝트 운영 종료 시 불필요하게 유지하지 않습니다.

---

## 19. kafka.t3.small 운영 기준

`kafka.t3.small`은 현재 프로젝트의
낮은 트래픽과 제한된 운영 기간을 고려한 비용 우선 구성입니다.

다음 상황에서는 더 큰 Broker Instance를 검토합니다.

```text
CPU Credit 부족
지속적인 CPU 사용률 증가
Consumer Lag 증가
Connection 증가
Partition 증가
처리량 증가
```

현재 구성을 실제 상용 대규모 서비스의
일반적인 Broker Size 기준으로 사용하지 않습니다.

프로젝트 트래픽 증가 시
M5 또는 M7g 계열로 확장하는 방안을 검토합니다.

---

## 20. 운영 종료 전 확인

MSK Cluster는 바로 삭제하지 않습니다.

다음 항목을 먼저 확인합니다.

```text
1. 실제 Kafka Producer가 더 이상 사용되지 않는가
2. 실제 Kafka Consumer가 더 이상 사용되지 않는가
3. 처리하지 않은 중요한 메시지가 없는가
4. 필요한 Kafka 데이터 백업이 완료되었는가
5. ECS에서 MSK에 의존하는 기능이 제거되었는가
6. KAFKA_BOOTSTRAP_SERVERS가 더 이상 필요하지 않은가
7. MSK IAM Policy가 다른 기능에서 사용되지 않는가
8. MSK Security Group이 다른 리소스에서 사용되지 않는가
```

실제 도메인 Kafka 기능이 적용된 이후에는
단순히 Cluster부터 삭제하면 안 됩니다.

---

## 21. 운영 종료 및 삭제 절차

### 1단계: MSK Cluster 확인

Cluster 이름을 기준으로 ARN을 조회합니다.

MSK_CLUSTER_NAME="otboo-prod-msk"

MSK_CLUSTER_ARNS="$(
aws kafka list-clusters-v2 \
--cluster-name-filter "$MSK_CLUSTER_NAME" \
--cluster-type-filter PROVISIONED \
--profile otboo \
--region ap-northeast-2 \
--query "ClusterInfoList[?ClusterName=='${MSK_CLUSTER_NAME}'].ClusterArn" \
--output text
)"

MSK_CLUSTER_COUNT="$(
printf '%s\n' "$MSK_CLUSTER_ARNS" \
| tr '\t' '\n' \
| sed '/^$/d;/^None$/d' \
| wc -l \
| tr -d ' '
)"

if [ "$MSK_CLUSTER_COUNT" -ne 1 ]; then
echo "MSK Cluster가 정확히 1개 조회되지 않았습니다."
exit 1
fi

MSK_CLUSTER_ARN="$(
printf '%s\n' "$MSK_CLUSTER_ARNS" \
| tr '\t' '\n' \
| sed '/^$/d;/^None$/d'
)"

값 자체를 README나 Issue에 복사하지 않습니다.

Cluster 상태를 확인합니다.

```bash
aws kafka describe-cluster-v2 \
  --cluster-arn "$MSK_CLUSTER_ARN" \
  --profile otboo \
  --region ap-northeast-2 \
  --query 'ClusterInfo.{
    Name:ClusterName,
    State:State
  }' \
  --output table
```

### 2단계: 애플리케이션 의존성 제거

실제 Kafka Producer·Consumer가 존재하는 경우
먼저 애플리케이션에서 MSK 사용을 중단한 버전을 배포합니다.

영구적으로 MSK를 제거하는 경우
Task Definition의 다음 환경변수도 제거합니다.

```text
KAFKA_BOOTSTRAP_SERVERS
```

### 3단계: Cluster 삭제

중요한 데이터와 의존성을 모두 확인한 뒤 삭제합니다.

```bash
aws kafka delete-cluster \
  --cluster-arn "$MSK_CLUSTER_ARN" \
  --profile otboo \
  --region ap-northeast-2
```

MSK Cluster 삭제는 복구할 수 없는 작업이므로
실제 운영 데이터가 필요한 경우 반드시 삭제 전에 백업합니다.

### 4단계: Cluster 삭제 확인

```bash
aws kafka list-clusters-v2 \
  --cluster-name-filter otboo-prod-msk \
  --profile otboo \
  --region ap-northeast-2 \
  --query 'ClusterInfoList[].{
    Name:ClusterName,
    State:State
  }' \
  --output table
```

Cluster가 더 이상 조회되지 않는 것을 확인합니다.

### 5단계: MSK Security Group 정리

Cluster 삭제 후 다음 Security Group이
다른 리소스에서 사용되지 않는지 확인합니다.

```text
otboo-prod-msk-sg
```

다른 리소스에서 사용하지 않는 경우에만 삭제합니다.

### 6단계: MSK IAM Policy 정리

다음 Client Policy가 더 이상 필요하지 않은지 확인합니다.

```text
otboo-prod-msk-client-policy
```

다른 Kafka 환경에서 사용하지 않는 경우
ECS Task Role에서 Policy를 분리한 뒤 삭제합니다.

ECS Task Role 자체는 S3 등 다른 운영 권한에도 사용하므로
MSK 종료를 이유로 삭제하지 않습니다.

---

## 22. 삭제하지 않는 공용 리소스

MSK 운영 종료 시 다음 공용 인프라는 삭제하지 않습니다.

```text
VPC
ECS Cluster
ECS Application Security Group
ECS Task Role
기존 Private Subnet
Private Route Table
RDS
Redis
ECR Repository
ALB
```

특히 MSK에서 사용한 Private Subnet은
RDS 등 다른 운영 리소스에서도 사용하므로
MSK Cluster 삭제와 함께 제거하면 안 됩니다.

---

## 23. 운영 원칙 요약

```text
Local
→ Docker Compose Kafka / KRaft

Production
→ Amazon MSK Provisioned Standard
→ Kafka 3.9.x / ZooKeeper
→ kafka.t3.small × 2
→ Private VPC
→ Public Access OFF
→ IAM
→ SASL_SSL
→ TCP 9098
→ ECS Task Role
```

애플리케이션에서는 Kafka Broker 주소나
AWS 인증정보를 소스 코드에 저장하지 않습니다.

Topic과 Consumer Group은
`docs/kafka/README.md`의 공통 명명 규칙을 따릅니다.

실제 도메인의 Producer·Consumer 적용은
각 도메인의 요구사항을 검토한 후 별도 이슈에서 진행합니다.