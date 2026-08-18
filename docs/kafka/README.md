# Kafka 공통 메시징 기준

## 1. 목적

서비스 내 비동기 이벤트 처리와 도메인 간 메시지 전달에 Kafka를 사용할 수 있도록
공통 개발 및 운영 기준을 정의합니다.

현재 단계에서는 특정 도메인의 비즈니스 이벤트를 Kafka로 전환하지 않으며,
각 도메인에서 Producer와 Consumer를 구현할 때 공통으로 따를 기준을 정의합니다.

---

## 2. 로컬 개발 환경

로컬 Kafka는 Docker Compose를 통해 실행합니다.

- Kafka 이미지: `apache/kafka:3.9.2`
- 실행 모드: KRaft
- 기본 포트: `9092`
- 로컬 접근 주소: `localhost:9092`

Spring Boot 로컬 환경에서는 다음 환경변수를 사용합니다.

```text
KAFKA_BOOTSTRAP_SERVERS
```

환경변수가 지정되지 않은 경우 기본값은 다음과 같습니다.

```text
localhost:9092
```

---

## 3. Topic 명명 규칙

Topic 이름은 다음 형식을 사용합니다.

```text
otboo.<domain>.<event>.v<version>
```

### 규칙

- 모두 소문자를 사용합니다.
- 각 영역은 `.`으로 구분합니다.
- 어떤 도메인에서 발생한 어떤 이벤트인지 이름만으로 식별할 수 있도록 작성합니다.
- 이벤트 이름은 완료된 사실을 나타내도록 작성합니다.
- 메시지 스키마의 호환되지 않는 변경이 필요한 경우 Topic 버전을 증가시킵니다.

### 예시

```text
otboo.notification.created.v1
otboo.feed.created.v1
otboo.direct-message.created.v1
```

위 Topic들은 명명 규칙을 설명하기 위한 예시이며,
실제 도메인별 Topic은 해당 Kafka 적용 이슈에서 결정합니다.

---

## 4. DLT 명명 규칙

Consumer가 메시지 처리에 최종 실패한 경우 사용하는
Dead Letter Topic은 원본 Topic 뒤에 `.dlt`를 추가합니다.

```text
<원본-topic>.dlt
```

예시:

```text
otboo.notification.created.v1
→ otboo.notification.created.v1.dlt
```

DLT는 실패한 메시지의 원본 Partition을 유지하도록 구성합니다.

따라서 DLT의 Partition 수는 원본 Topic의 Partition 수 이상으로 구성합니다.

```text
Source Topic Partition 수 <= DLT Partition 수
```

예를 들어 원본 Topic이 3개의 Partition을 사용한다면
해당 DLT도 최소 3개의 Partition을 사용합니다.

이를 통해 원본 Topic과 실패 Topic의 관계뿐 아니라
실패 메시지의 Partition 매핑도 일관되게 유지합니다.

---

## 5. Consumer Group 명명 규칙

Consumer Group은 다음 형식을 사용합니다.

```text
otboo-<domain-or-purpose>-<role>-consumer
```

### 규칙

- Consumer Group은 메시지 자체가 아니라 메시지를 처리하는 목적을 기준으로 구분합니다.
- 서로 독립적으로 동일한 메시지를 받아야 하는 Consumer는 서로 다른 Consumer Group을 사용합니다.
- 하나의 처리 작업을 여러 Consumer 인스턴스가 분담해야 하는 경우 동일한 Consumer Group을 사용합니다.
- `consumer`, `otboo`, `kafka-consumer`처럼 처리 목적을 알 수 없는 이름은 사용하지 않습니다.

### 예시

```text
otboo-notification-sse-consumer
otboo-feed-index-consumer
```

위 Consumer Group들은 명명 규칙을 설명하기 위한 예시이며,
실제 Consumer Group은 해당 도메인의 Kafka 적용 시 결정합니다.

---

## 6. Consumer 실패 및 재시도 기준

공통 Consumer 오류 처리는 Spring Kafka의 `DefaultErrorHandler`를 사용합니다.

현재 공통 재시도 기준은 다음과 같습니다.

```text
최초 처리 1회
→ 실패
→ 1초 대기
→ 재시도 1회
→ 실패
→ 1초 대기
→ 재시도 2회
→ 최종 실패 처리
```

따라서 하나의 메시지에 대해 최대 3회의 처리 기회를 가집니다.

재시도 횟수를 모두 소진한 메시지는
`DeadLetterPublishingRecoverer`를 통해 원본 Topic의 DLT로 전달합니다.

도메인별 비즈니스 예외의 재시도 여부는
실제 도메인 Producer·Consumer 구현 시 별도로 결정합니다.

---

## 7. 테스트 기준

Kafka 공통 환경은 실제 운영 Kafka에 의존하지 않고
Embedded Kafka를 통해 기본 동작을 검증합니다.

현재 다음 동작을 테스트합니다.

- Producer가 전송한 메시지를 Consumer가 정상적으로 수신하는지 확인
- Consumer 처리 실패 시 설정된 횟수만큼 재시도하는지 확인
- 재시도를 모두 소진한 메시지가 DLT로 전달되는지 확인

로컬 Docker Kafka와 Spring Boot의 연결은
로컬 프로필을 이용해 별도로 검증합니다.

---

## 8. 도메인 적용 원칙

공통 Kafka 환경을 구성하는 것과
실제 도메인의 메시징 구조를 Kafka로 전환하는 작업은 분리합니다.

피드, 알림, DM 등의 비즈니스 이벤트를 Kafka Producer·Consumer로 전환하는 작업은
각 도메인의 요구사항과 메시지 전달 보장 범위를 검토한 후 별도 이슈에서 진행합니다.

WebSocket·SSE의 다중 Task 정합성 문제 역시
Kafka를 기본 해결책으로 가정하지 않고 실제 검증 결과를 바탕으로 별도로 결정합니다.

---

## 9. 운영 환경

운영 Kafka는 Amazon MSK Provisioned Standard를 사용합니다.

실제 운영 환경은 다음 구성으로 구축했습니다.

```text
Service: Amazon MSK Provisioned
Broker Type: Standard
Kafka Version: 3.9.x
Metadata Mode: ZooKeeper
Broker Size: kafka.t3.small
Availability Zones: 2
Brokers per AZ: 1
Total Brokers: 2
Authentication: IAM
Public Access: 비활성화
```

최초에는 `Kafka 3.9.x + KRaft + kafka.t3.small` 구성을 검토했으나,
실제 MSK 생성 과정에서 해당 조합의 Instance Type이 지원되지 않는 것을 확인했습니다.

KRaft에서 사용할 수 있는 더 큰 Broker Instance는
현재 프로젝트의 규모와 운영 기간에 비해 비용 증가 폭이 크다고 판단하여,
최종 운영 환경은 `Kafka 3.9.x + ZooKeeper + kafka.t3.small × 2`로 구성했습니다.

MSK는 ECS 애플리케이션과 동일한 VPC 내부에서 연결하며
Broker를 Public Internet에 직접 공개하지 않습니다.

IAM 인증 연결은 다음 구성을 사용합니다.

```text
Security Protocol: SASL_SSL
SASL Mechanism: AWS_MSK_IAM
Port: 9098
Authentication Identity: ECS Task Role
```

운영 Kafka 연결 주소는 다음 환경변수로 주입합니다.

```text
KAFKA_BOOTSTRAP_SERVERS
```

Broker 주소와 AWS 인증정보는 소스 코드에 직접 작성하지 않습니다.

Consumer Group은 각 Consumer의 목적에 따라 결정하므로
운영 공통 설정에는 전역 `group-id`를 지정하지 않습니다.

실제 MSK Cluster, Subnet, Security Group, IAM Policy,
ECS 연결, 운영 Produce·Consume 검증, 모니터링 및 삭제 절차는
다음 문서에서 관리합니다.

[Amazon MSK Provisioned 운영 Kafka 구성](../aws/msk/README.md)