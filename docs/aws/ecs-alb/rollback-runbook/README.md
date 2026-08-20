# ECS 실패 배포 롤백 Runbook

## 1. 문서 목적

운영 ECS Rolling Update 과정에서 신규 Task가 정상 기동되지 않거나
ALB Health Check를 통과하지 못하는 경우의 확인 및 복구 절차를 정의합니다.

운영 환경에서는 ECS Deployment Circuit Breaker의 자동 롤백을 우선 사용합니다.

자동 롤백이 완료되지 않거나 즉시 이전 정상 Revision으로 복구해야 하는 경우에는
이 문서의 수동 롤백 절차를 사용합니다.

- 관련 이슈: #223
- 대상 ECS Cluster: `otboo-prod-cluster`
- 대상 ECS Service: `otboo-prod-backend-service`
- Task Definition Family: `otboo-prod-backend`

---

## 2. 운영 배포 기준

현재 운영 ECS Service는 다음 기준으로 Rolling Update를 수행합니다.

| 항목 | 운영 기준 |
| --- | --- |
| Deployment Strategy | `ROLLING` |
| Desired Count | `1` |
| Minimum Healthy Percent | `100` |
| Maximum Percent | `200` |
| Deployment Circuit Breaker | 활성화 |
| Automatic Rollback | 활성화 |
| Health Check Grace Period | `120초` |
| ALB Health Check Path | `/actuator/health` |
| Health Check Interval | `30초` |
| Health Check Timeout | `5초` |
| Healthy Threshold | `2` |
| Unhealthy Threshold | `3` |
| Health Check Success Code | `200` |
| Deregistration Delay | `60초` |

### 장애 대응 전 실제 배포 설정 확인

문서에 기록된 운영 기준만 신뢰하지 않고 장애 대응 시점의 ECS Service 설정을 다시 확인합니다.

```bash
aws ecs describe-services \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'services[0].{
    Controller:deploymentController.type,
    Strategy:deploymentConfiguration.strategy,
    CircuitBreaker:deploymentConfiguration.deploymentCircuitBreaker.enable,
    Rollback:deploymentConfiguration.deploymentCircuitBreaker.rollback
  }' \
  --output table
```

자동 롤백을 기대하려면 최소한 다음 값을 확인합니다.

```text
Controller      ECS
Strategy        ROLLING
CircuitBreaker  True
Rollback        True
```

위 조건을 만족하지 않으면 Deployment Circuit Breaker 자동 롤백을 전제로 대응하지 않고
수동 롤백 절차로 전환합니다.

Desired Count가 `1`이더라도 `Maximum Percent=200`으로 설정되어 있으므로
Rolling Update 중에는 기존 Task와 신규 Task를 동시에 실행할 수 있습니다.

`Minimum Healthy Percent=100`이므로 신규 Task가 정상화되기 전에
기존 정상 Task를 먼저 종료하지 않는 것을 기본 배포 기준으로 합니다.

---

## 3. 기본 변수 설정

아래 명령은 AWS CLI를 이용한 운영 확인 및 복구 절차에서 공통으로 사용합니다.

```bash
export AWS_REGION="ap-northeast-2"
export AWS_PROFILE="otboo"
export ECS_CLUSTER="otboo-prod-cluster"
export ECS_SERVICE="otboo-prod-backend-service"
```

Target Group ARN은 현재 ECS Service에서 조회합니다.

```bash
TARGET_GROUP_ARN=$(
  aws ecs describe-services \
    --cluster "$ECS_CLUSTER" \
    --services "$ECS_SERVICE" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --query 'services[0].loadBalancers[0].targetGroupArn' \
    --output text
)

echo "$TARGET_GROUP_ARN"
```

---

## 4. 배포 실패 판단 기준

다음 중 하나 이상이 확인되면 신규 배포 실패 여부를 점검합니다.

- 신규 Deployment의 `rolloutState`가 `FAILED`
- 신규 Deployment가 장시간 `IN_PROGRESS` 상태로 유지됨
- 신규 Task가 반복적으로 생성되고 종료됨
- ALB Target이 `unhealthy` 상태가 됨
- `/actuator/health` Health Check가 지속적으로 실패함
- ECS Service Event에 `deployment failed`가 기록됨
- 신규 Revision이 정상 Target으로 전환되지 못함
- 신규 배포 이후 5XX 또는 연결 오류가 지속적으로 증가함

### 4.1 Deployment 상태 확인

```bash
aws ecs describe-services \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'services[0].deployments[].{
    Status:status,
    RolloutState:rolloutState,
    Reason:rolloutStateReason,
    TaskDefinition:taskDefinition,
    Desired:desiredCount,
    Running:runningCount,
    Pending:pendingCount,
    FailedTasks:failedTasks
  }' \
  --output table
```

정상 배포가 완료된 경우 일반적으로 다음 상태를 확인합니다.

- `Status`: `PRIMARY`
- `RolloutState`: `COMPLETED`
- `Desired`: `1`
- `Running`: `1`
- `Pending`: `0`

### 4.2 ALB Target 상태 확인

```bash
aws elbv2 describe-target-health \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'TargetHealthDescriptions[].{
    Target:Target.Id,
    Port:Target.Port,
    State:TargetHealth.State,
    Reason:TargetHealth.Reason,
    Description:TargetHealth.Description
  }' \
  --output table
```

정상 Target은 다음 상태여야 합니다.

```text
State: healthy
Reason: None
```

Health Check에 실패한 신규 Task는 다음과 같이 표시될 수 있습니다.

```text
State: unhealthy
Reason: Target.ResponseCodeMismatch
Description: Health checks failed with these codes: [502]
```

---

## 5. 이전 정상 Task Definition 확인

롤백 전에 반드시 이전 정상 Task Definition Revision을 식별합니다.

### 5.1 현재 Service Revision 확인

```bash
aws ecs describe-services \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'services[0].taskDefinition' \
  --output text
```

### 5.2 최근 성공한 Service Deployment 확인

Task Definition의 등록 순서만으로 롤백 대상을 선택하지 않습니다.

먼저 성공적으로 완료된 Service Deployment 이력을 조회합니다.

```bash
aws ecs list-service-deployments \
  --cluster "$ECS_CLUSTER" \
  --service "$ECS_SERVICE" \
  --status SUCCESSFUL \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceDeployments[].{
    DeploymentArn:serviceDeploymentArn,
    ServiceRevisionArn:targetServiceRevisionArn,
    Status:status,
    StartedAt:startedAt,
    FinishedAt:finishedAt
  }' \
  --output table
```

롤백 후보 Deployment의 `targetServiceRevisionArn`을 확인한 뒤 해당 Service Revision을 조회합니다.

```bash
aws ecs describe-service-revisions \
  --service-revision-arns "<정상_SERVICE_REVISION_ARN>" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceRevisions[0].{
    ServiceRevision:serviceRevisionArn,
    TaskDefinition:taskDefinition
  }' \
  --output table
```

여기서 확인된 `TaskDefinition`을 롤백 후보로 사용합니다.

현재 진행 중 Deployment의 `rolloutState=COMPLETED` 여부는
`describe-services`를 통해 별도로 확인합니다.

Revision 번호가 최신이라는 이유만으로 롤백 대상으로 선택하지 않습니다.

다음 조건을 함께 확인합니다.

- 성공한 Service Deployment에 연결된 Task Definition
- 기존 ECS Deployment가 정상 완료된 이력이 있음
- ALB Target이 `healthy`였음
- 운영 요청이 정상적으로 처리되었음
- 필요한 환경변수와 Secret 구성이 검증되었음

이전 정상 Deployment 또는 정상 Revision을 확인할 수 없는 경우
자동으로 임의의 Revision을 선택하지 않고 장애 원인을 확인한 뒤 수동 복구 대상을 결정합니다.

---

## 6. Deployment Circuit Breaker 자동 롤백

운영 Service는 Deployment Circuit Breaker와 자동 롤백을 활성화합니다.

신규 Revision이 정상화되지 못하면 ECS가 배포 실패를 판단하고
이전 정상 Deployment로 자동 롤백합니다.

### 6.1 자동 롤백 흐름

```text
신규 Task 기동
→ 신규 Target 등록
→ ALB Health Check 수행
→ Health Check 실패
→ 신규 Target unhealthy
→ 실패 Task 제거 및 재시도
→ ECS Deployment 실패 판단
→ 이전 정상 Deployment로 자동 Rollback
→ 정상 Task 유지 또는 복구
→ Deployment COMPLETED
```

신규 Task가 `unhealthy` 상태인 동안에도 기존 정상 Task가 `healthy` 상태라면
ALB는 정상 Target을 통해 서비스 트래픽을 계속 처리합니다.

### 6.2 ECS Service Event 확인

```bash
aws ecs describe-services \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'services[0].events[].[createdAt,message]' \
  --output text \
  | grep -Ei \
    'deployment|rollback|health|unhealthy|failed|deregister|register' \
  | head -40
```

자동 롤백이 발생한 경우 다음과 같은 흐름을 확인합니다.

```text
Target registered
→ Target unhealthy
→ Health Check failed
→ Target deregistered
→ deployment failed
→ rolling back to deployment ...
→ previous deployment completed
```

### 6.3 자동 롤백 완료 기준

다음 조건을 모두 확인합니다.

- 이전 정상 Task Definition으로 복구됨
- Deployment 상태 `COMPLETED`
- Desired Count `1`
- Running Count `1`
- Pending Count `0`
- ALB Target `healthy`
- 운영 도메인 요청 정상
- 신규 오류가 지속적으로 발생하지 않음

---

## 7. 수동 롤백 절차

자동 롤백이 아직 시작되지 않았거나
운영자가 진행 중인 실패 Deployment를 즉시 이전 정상 Service Revision으로 되돌려야 하는 경우
현재 Service Deployment 상태를 먼저 확인합니다.

### 7.1 진행 중 Service Deployment 확인

```bash
aws ecs list-service-deployments \
  --cluster "$ECS_CLUSTER" \
  --service "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceDeployments[].{
    DeploymentArn:serviceDeploymentArn,
    Status:status,
    ServiceRevisionArn:targetServiceRevisionArn,
    StatusReason:statusReason
  }' \
  --output table
```

현재 문제가 발생한 Deployment의 ARN과 상태를 확인합니다.

`PENDING` 또는 `IN_PROGRESS` 상태에서 즉시 이전 정상 Service Revision으로 되돌려야 하는 경우
해당 Deployment를 Rollback 처리합니다.

### 7.2 진행 중 Deployment 즉시 Rollback

```bash
FAILED_DEPLOYMENT_ARN="<실패_SERVICE_DEPLOYMENT_ARN>"

aws ecs stop-service-deployment \
  --service-deployment-arn "$FAILED_DEPLOYMENT_ARN" \
  --stop-type ROLLBACK \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

이 명령은 진행 중 Deployment를 이전 Service Revision으로 롤백하도록 ECS에 요청합니다.

Deployment 상태가 이미 `ROLLBACK_REQUESTED` 또는 `ROLLBACK_IN_PROGRESS`라면
새로운 배포를 추가로 생성하지 않고 기존 Rollback 완료를 기다립니다.

다음 명령으로 상태를 확인합니다.

```bash
aws ecs describe-service-deployments \
  --service-deployment-arns "$FAILED_DEPLOYMENT_ARN" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceDeployments[0].{
    Status:status,
    StatusReason:statusReason,
    Rollback:rollback,
    CircuitBreaker:deploymentCircuitBreaker
  }' \
  --output json
```

최종적으로 `ROLLBACK_SUCCESSFUL` 또는 정상 Service 안정화를 확인합니다.

### 7.3 Service 안정화 확인

```bash
aws ecs wait services-stable \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

### 7.4 명시적 Task Definition Revision 복구

진행 중 Deployment의 Rollback 처리가 완료된 이후에도
특정 정상 Task Definition Revision으로 명시적으로 복구해야 하는 경우에만
`update-service`를 사용합니다.

먼저 이전 정상 Revision을 확인합니다.

```bash
ROLLBACK_TASK_DEF="otboo-prod-backend:<정상_REVISION>"
```

그다음 Service의 Task Definition을 변경합니다.

```bash
aws ecs update-service \
  --cluster "$ECS_CLUSTER" \
  --service "$ECS_SERVICE" \
  --task-definition "$ROLLBACK_TASK_DEF" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

실패 Task나 기존 정상 Task를 직접 `stop-task`하여 롤백하지 않습니다.

`update-service`를 통한 명시적 Revision 복구는
진행 중 Deployment 상태를 확인하고 필요한 Rollback 처리를 완료한 이후 수행합니다.

---

## 8. ALB 트래픽 복구 확인

롤백 이후 Target Group 상태를 확인합니다.

```bash
aws elbv2 describe-target-health \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'TargetHealthDescriptions[].{
    Target:Target.Id,
    Port:Target.Port,
    State:TargetHealth.State,
    Reason:TargetHealth.Reason,
    Description:TargetHealth.Description
  }' \
  --output table
```

최종적으로 운영 트래픽을 처리하는 Target이 `healthy` 상태여야 합니다.

실패 Revision의 Target이 남아 있는 경우 상태를 확인하고
ECS Service의 Deployment가 완전히 종료되었는지 다시 확인합니다.

---

## 9. 애플리케이션 정상 응답 확인

ECS와 ALB가 정상화된 뒤 실제 운영 도메인도 확인합니다.

```bash
curl -I https://otboo.work/
```

Health Check도 확인합니다.

```bash
curl -sS https://otboo.work/actuator/health
```

정상 응답 여부와 함께 CloudWatch Logs에서
롤백 이후 새로운 오류가 지속적으로 발생하지 않는지 확인합니다.

---

## 10. 실패 Revision 처리

실패 검증용 또는 더 이상 사용하지 않는 Task Definition Revision은
현재 Service의 모든 Deployment에서 더 이상 참조하지 않고,
추가 롤백 대상으로도 사용할 필요가 없는 경우에만 비활성화합니다.

현재 Deployment별 Task Definition을 확인합니다.

```bash
aws ecs describe-services \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'services[0].deployments[].{
    Status:status,
    RolloutState:rolloutState,
    TaskDefinition:taskDefinition,
    Desired:desiredCount,
    Running:runningCount,
    Pending:pendingCount
  }' \
  --output table
```

실패 Revision이 `PRIMARY` 또는 `ACTIVE` Deployment에서 참조되고 있다면
즉시 비활성화하지 않습니다.

Service Deployment 이력을 확인하여 실패 Revision이 어떤 Service Revision과 연결되어 있는지 확인합니다.

```bash
aws ecs list-service-deployments \
  --cluster "$ECS_CLUSTER" \
  --service "$ECS_SERVICE" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceDeployments[].{
    Status:status,
    ServiceRevisionArn:targetServiceRevisionArn,
    DeploymentArn:serviceDeploymentArn
  }' \
  --output table
```

확인이 필요한 Deployment ARN을 지정합니다.

```bash
DEPLOYMENT_ARN="<확인할_SERVICE_DEPLOYMENT_ARN>"
```

해당 Deployment의 이전 Service Revision과 배포 대상 Service Revision을 확인합니다.

```bash
aws ecs describe-service-deployments \
  --service-deployment-arns "$DEPLOYMENT_ARN" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceDeployments[0].{
    Status:status,
    SourceServiceRevisions:sourceServiceRevisions[].arn,
    TargetServiceRevision:targetServiceRevision.arn
  }' \
  --output json
```

`sourceServiceRevisions`는 배포 이전에 사용 중이던 Service Revision이고,
`targetServiceRevision`은 해당 Deployment에서 배포하려던 Service Revision입니다.

확인할 Service Revision ARN을 지정합니다.

```bash
SERVICE_REVISION_ARN="<확인할_SERVICE_REVISION_ARN>"
```

Service Revision이 실제로 사용하는 Task Definition을 확인합니다.

```bash
aws ecs describe-service-revisions \
  --service-revision-arns "$SERVICE_REVISION_ARN" \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE" \
  --query 'serviceRevisions[0].{
    ServiceRevisionArn:serviceRevisionArn,
    TaskDefinition:taskDefinition
  }' \
  --output table
```

조회된 `TaskDefinition`이 비활성화하려는 실패 Revision과 일치하는지 확인합니다.

다음 조건을 모두 만족할 때만 실패 Revision을 비활성화합니다.

- 현재 `PRIMARY` Deployment에서 사용하지 않음
- 전환 중인 `ACTIVE` Deployment에서 사용하지 않음
- 진행 중인 Rollback에서 필요하지 않음
- 성공한 Service Deployment의 정상 롤백 후보가 아님
- Service Revision 조회 결과 비활성화하려는 실패 Task Definition과 일치함
- 의도적으로 생성한 실패 또는 검증용 Revision임

조건을 확인한 뒤 비활성화합니다.

```bash
aws ecs deregister-task-definition \
  --task-definition otboo-prod-backend:<실패_REVISION> \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

Task Definition을 deregister하면 `INACTIVE` 상태가 되며
새로운 Task 실행이나 Service 업데이트 대상으로 다시 사용할 수 없습니다.

---

## 11. 운영 장애 대응 순서

실제 배포 장애가 발생한 경우 다음 순서로 대응합니다.

### 1단계. 현재 서비스 상태 확인

- 현재 Task Definition 확인
- Deployment 상태 확인
- Running/Pending Task 수 확인
- ALB Target Health 확인

### 2단계. 기존 정상 서비스 유지 여부 확인

- 기존 정상 Task가 실행 중인지 확인
- 기존 Target이 `healthy`인지 확인
- 운영 도메인이 정상 응답하는지 확인

기존 정상 서비스가 유지되고 있다면
실패 신규 Task를 직접 종료하지 않고 Circuit Breaker 동작을 우선 확인합니다.

### 3단계. 자동 롤백 확인

ECS Service Event에서 다음 항목을 확인합니다.

- `deployment failed`
- `rolling back`
- 이전 Deployment `completed`

### 4단계. 자동 롤백 실패 또는 긴급 복구 시 수동 롤백

이전 정상 Task Definition Revision을 식별한 뒤
`update-service`를 사용해 Service Revision을 되돌립니다.

### 5단계. 복구 완료 검증

- Deployment `COMPLETED`
- Running Count 정상
- Pending Count `0`
- ALB Target `healthy`
- 운영 HTTP 요청 정상
- CloudWatch 신규 오류 없음

### 6단계. 장애 원인 확인

서비스 복구를 먼저 완료한 뒤 실패 Revision의 다음 항목을 분석합니다.

- 애플리케이션 시작 로그
- 환경변수 및 Secret
- IAM 권한
- DB·Redis·Elasticsearch 등 외부 연결
- Nginx Reverse Proxy
- Container Port
- ALB Health Check
- 이미지 자체 문제

원인 확인을 위해 정상 서비스의 설정을 임의로 변경하지 않습니다.

---

## 12. 하지 말아야 할 작업

운영 장애 대응 중 다음 작업은 피합니다.

- 현재 정상 Task를 먼저 수동 종료하지 않습니다.
- 실패 신규 Task를 반복해서 수동 재시작하지 않습니다.
- 원인을 확인하지 않은 상태에서 여러 AWS 설정을 동시에 변경하지 않습니다.
- 정상 Revision을 확인하지 않고 Revision 번호만 보고 롤백하지 않습니다.
- 현재 Service가 사용 중인 Task Definition을 비활성화하지 않습니다.
- 운영 복구 전에 실패 Revision 수정 작업을 우선하지 않습니다.
- DB·Redis·Security Group 등 관련 없는 운영 리소스를 임의로 변경하지 않습니다.

서비스 복구와 원인 분석을 분리해서 진행합니다.

---

## 13. Issue #223 검증 결과

Issue #223에서는 실패 Task Definition Revision을 의도적으로 생성하여
자동 롤백과 수동 롤백 절차를 운영 환경에서 검증했습니다.

### 13.1 실패 상황 구성

정상 Revision을 기준으로 실패 테스트용 Revision을 생성하고
Backend의 서버 포트를 `9999`로 변경했습니다.

운영 Nginx는 기존과 동일하게 Backend `8080` 포트로 요청을 전달하므로
실패 Revision에서는 Nginx가 Backend에 정상적으로 연결하지 못하는 상황을 재현했습니다.

```text
ALB
→ Nginx :80
→ Backend :8080 연결 시도
→ 실패 Revision Backend는 :9999에서 실행
→ /actuator/health 응답 실패
→ HTTP 502
```

### 13.2 ALB Health Check 검증

실패 신규 Target에서 다음 상태를 확인했습니다.

```text
State: unhealthy
Reason: Target.ResponseCodeMismatch
Health Check Response: 502
```

동시에 기존 정상 Target은 `healthy` 상태를 유지했습니다.

이를 통해 비정상 신규 Task가 정상 트래픽 처리 대상으로 전환되지 않고
기존 정상 Task가 서비스 요청을 계속 처리하는 것을 확인했습니다.

### 13.3 자동 롤백 검증

실패 Revision의 Target이 반복적으로 Health Check에 실패한 뒤
ECS Service Event에서 다음 흐름을 확인했습니다.

```text
신규 Target 등록
→ Health Check 502
→ Target unhealthy
→ 실패 Target deregister
→ deployment failed
→ 이전 정상 Deployment로 rolling back
→ 이전 정상 Deployment completed
```

최종적으로 이전 정상 Task Definition이 다시 운영 Service의 기준 Revision이 되었고,
ALB Target 역시 `healthy` 상태로 복구되었습니다.

실패 배포부터 자동 롤백 완료까지 일반 HTTP 요청을 연속으로 수행한 결과:

```text
총 요청: 871
HTTP 200: 871
Curl Error: 0
Non-200: 0
```

실패 배포 및 자동 롤백 과정에서도 일반 HTTP 요청 중단이 발생하지 않았습니다.

### 13.4 수동 롤백 검증

동일한 실패 Revision을 다시 배포한 뒤
신규 Target이 `unhealthy` 상태가 된 것을 확인했습니다.

자동 롤백 완료를 기다리지 않고 이전 정상 Task Definition Revision을 지정하여
다음 방식으로 Service를 수동 복구했습니다.

```bash
aws ecs update-service \
  --cluster "$ECS_CLUSTER" \
  --service "$ECS_SERVICE" \
  --task-definition otboo-prod-backend:<정상_REVISION> \
  --region "$AWS_REGION" \
  --profile "$AWS_PROFILE"
```

수동 롤백 이후 다음 상태를 확인했습니다.

- 이전 정상 Task Definition 복구
- `PRIMARY / COMPLETED`
- Desired Count `1`
- Running Count `1`
- Pending Count `0`
- ALB Target `healthy`
- 수동 롤백 중 Curl Error `0건`
- 수동 롤백 중 Non-200 응답 `0건`

이를 통해 Deployment Circuit Breaker 자동 롤백과
이전 정상 Task Definition을 이용한 수동 롤백 절차를 모두 검증했습니다.

---

## 14. 최종 결론

운영 ECS Service는 다음 장애 대응 구조를 사용합니다.

```text
정상 Revision 운영
        ↓
신규 Revision Rolling Update
        ↓
ALB Health Check
        ↓
┌─────────────────────────────┐
│ 신규 Task 정상              │
│ → 신규 Target healthy       │
│ → 기존 Task 종료            │
│ → 배포 완료                 │
└─────────────────────────────┘

또는

┌─────────────────────────────┐
│ 신규 Task 비정상            │
│ → Target unhealthy          │
│ → 기존 정상 Task 유지       │
│ → Circuit Breaker 감지      │
│ → 자동 Rollback             │
└─────────────────────────────┘

자동 복구가 완료되지 않는 경우

        ↓

이전 정상 Task Definition 지정
        ↓
ECS update-service
        ↓
Service stable
        ↓
ALB healthy 확인
        ↓
수동 롤백 완료
```

운영 장애 발생 시에는 기존 정상 서비스의 유지 여부를 먼저 확인하고,
Deployment Circuit Breaker의 자동 롤백을 우선 사용합니다.

자동 복구가 완료되지 않거나 즉시 복구가 필요한 경우에만
검증된 이전 정상 Task Definition Revision을 이용해 수동 롤백합니다.