# Amazon RDS PostgreSQL 및 S3 구성

## 1. 구성 목적

운영 환경에서 사용할 PostgreSQL 데이터베이스와 프로필·의상 이미지 저장용 S3 Bucket을 구성합니다.

- RDS는 외부 인터넷에 공개하지 않습니다.
- RDS는 ECS Application Security Group에서만 접근할 수 있습니다.
- S3는 모든 공개 접근을 차단합니다.
- 애플리케이션은 고정 Access Key 대신 ECS Task Role을 통해 S3에 접근합니다.

---

## 2. 공통 정보

| 항목 | 값 |
| --- | --- |
| Project | `otboo` |
| Environment | `prod` |
| AWS Region | `ap-northeast-2` |
| ManagedBy | `manual` |

실제 비밀번호, Secret 값, AWS Access Key는 Git 저장소에 보관하지 않습니다.

---

## 3. 네트워크 구성

### VPC

| 항목 | 값 |
| --- | --- |
| VPC ID | `vpc-00db8f2d24cb038f9` |
| CIDR | `172.31.0.0/16` |
| 유형 | Default VPC 사용 |

기존 Default Subnet은 Internet Gateway 경로가 있는 Public Subnet이므로 RDS에는 사용하지 않습니다.

### RDS 전용 Private Subnet

| 이름 | Subnet ID | 가용 영역 | CIDR |
| --- | --- | --- | --- |
| `otboo-prod-db-private-2a` | `subnet-08530421cc2349e5b` | `ap-northeast-2a` | `172.31.64.0/24` |
| `otboo-prod-db-private-2c` | `subnet-092384d9d96e20d7d` | `ap-northeast-2c` | `172.31.65.0/24` |

두 Subnet 모두 Public IP 자동 할당이 비활성화되어 있습니다.

### DB 전용 Route Table

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-db-private-rt` |
| Route Table ID | `rtb-0c76bad530924414f` |
| 경로 | `172.31.0.0/16 → local` |
| Internet Gateway 경로 | 없음 |
| NAT Gateway 경로 | 없음 |

### DB Subnet Group

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-db-subnet-group` |
| 가용 영역 | `ap-northeast-2a`, `ap-northeast-2c` |
| 상태 | `Complete` |

---

## 4. Security Group 구성

### ECS Application Security Group

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-app-sg` |
| Security Group ID | `sg-0721e7157c9051e76` |
| 현재 인바운드 | 없음 |

ALB에서 ECS로 접근하는 규칙은 ECS·ALB 구성 시 추가합니다.

### RDS Security Group

| 항목 | 값 |
| --- | --- |
| 이름 | `otboo-prod-rds-sg` |
| Security Group ID | `sg-07c39630135e7d0e5` |
| 허용 포트 | TCP `5432` |
| 허용 소스 | `otboo-prod-app-sg` |
| CIDR 기반 인바운드 | 없음 |

```text
otboo-prod-app-sg
        │
        │ TCP 5432
        ▼
otboo-prod-rds-sg
```

---

## 5. RDS PostgreSQL 구성

| 항목 | 값 |
| --- | --- |
| DB Identifier | `otboo-prod-postgres` |
| Engine | PostgreSQL |
| Engine Version | `18.4` |
| DB Name | `otboo` |
| Master Username | `otboo_admin` |
| Instance Class | `db.t4g.micro` |
| 배포 방식 | Single-AZ |
| Storage | gp3 `20 GiB` |
| Storage Auto Scaling | 최대 `30 GiB` |
| Port | `5432` |
| Public Access | 비활성화 |
| Storage Encryption | 활성화 |
| Backup Retention | 7일 |
| Deletion Protection | 활성화 |
| Auto Minor Version Upgrade | 활성화 |
| Extended Support | 비활성화 |
| 상태 | `available` |

RDS 마스터 비밀번호는 RDS가 AWS Secrets Manager에서 자동 생성하고 관리하도록 구성했습니다.

Secret ARN과 실제 비밀번호는 문서 및 Git 저장소에 기록하지 않습니다.

운영 애플리케이션은 마스터 계정 대신 별도의 애플리케이션 DB 계정을 사용해야 합니다.

### 운영 DB 환경변수

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
```

`DB_HOST`, `DB_PORT`, `DB_NAME`은 ECS 일반 환경변수로 관리합니다.

`DB_USERNAME`, `DB_PASSWORD`는 운영 환경에서 AWS Secrets Manager로 관리하고,
ECS Task Definition의 `secrets`를 통해 컨테이너에 주입합니다.

운영 DB 스키마 생성은 별도의 Flyway 작업에서 진행합니다. 실제 RDS 연결은 Flyway 작업과 ECS 구성이 완료된 후 검증합니다.

---

## 6. S3 Bucket 구성

| 항목 | 값 |
| --- | --- |
| Bucket Name | `otboo-prod-assets-310567229825-ap-northeast-2` |
| Region | `ap-northeast-2` |
| Object Ownership | `BucketOwnerEnforced` |
| ACL | 비활성화 |
| Block Public Access | 전체 활성화 |
| Public 상태 | `false` |
| 기본 암호화 | SSE-S3 (`AES256`) |
| SSE-C | 차단 |
| HTTPS 강제 | 적용 |
| Versioning | 활성화 |

### Public Access Block

다음 설정을 모두 활성화했습니다.

```text
BlockPublicAcls=true
IgnorePublicAcls=true
BlockPublicPolicy=true
RestrictPublicBuckets=true
```

### HTTPS 강제

Bucket Policy에서 `aws:SecureTransport=false`이고
`aws:PrincipalIsAWSService=false`인 요청을 거부합니다.

이를 통해 암호화되지 않은 일반 요청을 차단하면서,
AWS 서비스 주체의 요청은 거부 조건에서 제외합니다.

정책 파일:

```text
docs/aws/rds-s3/s3-tls-bucket-policy.json
```

### 객체 경로

```text
profiles/{userId}/{uuid}.{extension}
clothes/{userId}/{uuid}.{extension}
```

원본 파일명을 객체 Key로 직접 사용하지 않고 UUID 기반 파일명을 사용합니다.

### Lifecycle

| 규칙 | 설정 |
| --- | --- |
| 이전 객체 버전 | 7일 후 삭제 |
| 만료된 Delete Marker | 자동 정리 |
| 미완료 Multipart Upload | 1일 후 중단 |

정책 파일:

```text
docs/aws/rds-s3/s3-lifecycle-policy.json
```

현재 사용 중인 최신 객체를 기간 기준으로 자동 삭제하는 규칙은 적용하지 않았습니다.

### CORS

현재 브라우저의 S3 직접 업로드 방식이 확정되지 않아 CORS는 설정하지 않았습니다.

향후 Presigned URL 방식으로 브라우저가 S3에 직접 접근하는 경우 운영 프론트엔드 Origin만 허용하도록 구성합니다.

---

## 7. ECS Task Role 및 S3 권한

| 항목 | 값 |
| --- | --- |
| Role Name | `otboo-prod-ecs-task-role` |
| IAM Policy | `otboo-prod-s3-assets-policy` |
| 신뢰 서비스 | `ecs-tasks.amazonaws.com` |

허용 권한:

```text
s3:GetBucketLocation
s3:ListBucket
s3:GetObject
s3:PutObject
s3:DeleteObject
```

허용 경로:

```text
profiles/*
clothes/*
```

허용하지 않은 주요 권한:

```text
s3:*
s3:PutObjectAcl
s3:DeleteObjectVersion
s3:DeleteBucket
s3:PutBucketPolicy
s3:PutBucketPublicAccessBlock
```

IAM Policy Simulator를 통해 다음을 확인했습니다.

- `profiles/*`, `clothes/*` 객체 조회·업로드·삭제: `allowed`
- 그 외 객체 경로: `implicitDeny`
- 버킷 삭제 및 정책 변경: `implicitDeny`

ECS Task Definition의 `taskRoleArn`에 이 역할을 연결합니다.

ECR 이미지 Pull, CloudWatch Logs 전송 및 Secret 주입에 사용하는 ECS Task Execution Role은 별도로 구성합니다.

---

## 8. 운영 S3 환경변수

```text
AWS_REGION=ap-northeast-2
S3_BUCKET=otboo-prod-assets-310567229825-ap-northeast-2
```

다음 값은 애플리케이션 환경변수로 사용하지 않습니다.

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_SESSION_TOKEN
```

애플리케이션은 ECS Task Role의 임시 자격 증명을 사용합니다.

---

## 9. 정책 파일

| 파일 | 용도 |
| --- | --- |
| `s3-tls-bucket-policy.json` | 암호화되지 않은 HTTP 요청 거부 |
| `s3-lifecycle-policy.json` | 이전 버전과 미완료 업로드 정리 |
| `ecs-task-trust-policy.json` | ECS Task Role 신뢰 정책 |
| `s3-assets-policy.json` | 프로필·의상 객체 최소 권한 |

---

## 10. 후속 연동

다음 작업은 관련 기능과 ECS 구성이 완료된 후 진행합니다.

- Flyway를 이용한 운영 DB 스키마 적용
- 애플리케이션용 DB 사용자 및 Secret 구성
- ECS Task Definition에 환경변수와 Secret 연결
- ECS Task에서 RDS 연결 검증
- ECS Task에서 S3 업로드·조회·삭제 검증
- 필요 시 Presigned URL 및 CORS 구성

## 11. 비용 및 리소스 종료 기준

### 예상 비용

비용 절감을 위해 다음 사양으로 구성했습니다.

- RDS: PostgreSQL 18.4
- 인스턴스 클래스: `db.t4g.micro`
- 배포 방식: Single-AZ
- 스토리지: gp3 20GiB
- 스토리지 자동 확장 한도: 30GiB
- 백업 보존 기간: 7일
- S3: Standard Storage 및 Versioning 사용

예상 비용은 AWS Pricing Calculator에서 서울 리전(`ap-northeast-2`) 기준으로 산정합니다.

| 항목 | 예상 비용 |
| --- |-----------|
| RDS PostgreSQL | 65.40 USD |
| RDS 스토리지 및 백업 | 별도 미산정 — RDS 전체 견적에 포함  |
| S3 저장·요청 비용 | 0.08 USD  |
| 월 예상 합계 | 65.48 USD |

산정일: 2026-08-03

산정 기준:

```text
- Region: Asia Pacific (Seoul)
- RDS Engine: PostgreSQL
- Instance Class: `db.t4g.micro`
- Deployment: Single-AZ
- Pricing Model: On-Demand
- 사용률: 월 100%
- Storage: gp3 20GiB
- S3: Standard Storage
- 월 예상 비용: 65.48 USD
```

실제 비용은 RDS 실행 시간, 저장 용량, 백업 용량, S3 요청 횟수 및 데이터 전송량에 따라 달라질 수 있습니다.

### 운영 종료 후 정리 기준

프로젝트 운영 및 시연이 종료되면 다음 순서로 리소스를 정리합니다.

1. RDS 데이터 보존 필요 여부를 확인합니다.
2. 데이터 보존이 필요하면 `otboo-prod-postgres-final-YYYYMMDD` 형식으로 최종 Snapshot을 생성합니다.
3. RDS의 Deletion Protection을 비활성화한 후 DB 인스턴스를 삭제합니다.
4. S3에 보존할 객체가 있는지 확인하고 필요한 파일을 백업합니다.
5. S3의 객체 버전과 Delete Marker를 모두 삭제한 후 Bucket을 삭제합니다.
6. ECS에서 사용하지 않는 것을 확인한 후 ECS Task Role에서 S3 IAM Policy를 분리합니다.
7. 사용하지 않는 IAM Policy와 ECS Task Role을 삭제합니다.
8. RDS와 ECS가 삭제된 뒤 DB Subnet Group, Security Group, Route Table 및 RDS 전용 Subnet을 삭제합니다.

최종 Snapshot을 생성하지 않고 RDS를 삭제하는 경우 복구할 수 없으므로, 삭제 전 데이터 보존 여부를 반드시 확인합니다.