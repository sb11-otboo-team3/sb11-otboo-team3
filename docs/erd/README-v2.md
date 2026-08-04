# 옷장을 부탁해 통합 ERD — 버전 2 (Flyway `V1__init_schema.sql` 반영)

[버전 1](./README.md)은 최초 설계 문서이고, 아래는 실제 Flyway 마이그레이션(`src/main/resources/db/migration/V1__init_schema.sql`)에 반영된 스키마입니다. 실제 구현된 `Grid`/`Weather`/`Clothes` 계열/`Follow`/`DirectMessage` 엔티티 및 팀 논의 결과를 기준으로 확정했습니다.

## 1. 사용자·인증 도메인

### `users`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 사용자 식별자 |
| `email` | VARCHAR(320) | NOT NULL, UNIQUE | 로그인 이메일 |
| `name` | VARCHAR(100) | NOT NULL | 사용자 이름 |
| `password_hash` | VARCHAR(255) | NOT NULL | 비밀번호 해시 값 |
| `role` | VARCHAR(20) | NOT NULL, DEFAULT `'USER'`, CHECK IN (`ADMIN`,`USER`) | 권한 |
| `locked` | BOOLEAN | NOT NULL, DEFAULT FALSE | 계정 잠금 여부 |
| `token_version` | BIGINT | NOT NULL, DEFAULT 0 | JWT 무효화 버전 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |

### `profiles`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `user_id` | UUID | PK, FK → `users.id` (ON DELETE RESTRICT) | 프로필 소유 사용자 |
| `image_url` | TEXT | NULL | 프로필 이미지 URL |
| `gender` | VARCHAR(20) | NULL, CHECK IN (`MALE`,`FEMALE`,`OTHER`) | 성별 |
| `birth_date` | DATE | NULL | 생년월일 |
| `latitude` | NUMERIC(9,6) | NULL | 위도 |
| `longitude` | NUMERIC(9,6) | NULL | 경도 |
| `x` | INTEGER | NULL | 기상청 격자 X |
| `y` | INTEGER | NULL | 기상청 격자 Y |
| `province` | VARCHAR(50) | NULL | 행정구역 시/도 |
| `city` | VARCHAR(50) | NULL | 행정구역 시/군/구 |
| `district` | VARCHAR(50) | NULL | 행정구역 읍/면/동 |
| `temp_sensitivity` | INTEGER | NULL, CHECK BETWEEN 1 AND 5 | 온도 민감도 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |

> 버전 1의 `location_id` FK 참조 대신, 위치 정보를 `x`/`y`/`province`/`city`/`district`로 인라인 저장하는 방식으로 바뀌었습니다.

### `oauth_accounts`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | OAuth 계정 식별자 |
| `user_id` | UUID | FK → `users.id` (ON DELETE RESTRICT), NOT NULL | 연동 사용자 |
| `provider` | VARCHAR(20) | NOT NULL, CHECK IN (`GOOGLE`,`KAKAO`) | OAuth 공급자 |
| `provider_user_id` | VARCHAR(255) | NOT NULL | 공급자 사용자 식별자 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 연동 시각 |

---

## 2. 위치·날씨 도메인

### `weather_grid` (버전 1의 `locations` 대체)

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 격자 식별자 |
| `x` | INTEGER | NOT NULL | 기상청 격자 X |
| `y` | INTEGER | NOT NULL | 기상청 격자 Y |
| `last_requested_at` | TIMESTAMPTZ | NOT NULL | 마지막 요청 시각 |
| `created_at` | TIMESTAMPTZ | NOT NULL | 생성 시각 |

> 실제 `Grid` 엔티티(`weather_grid` 테이블) 기준입니다. 버전 1의 `locations`(`location_names TEXT[]`, `updated_at` 포함)는 이전에 삭제된 `Location` 엔티티 기준이라 실제 코드와 맞지 않아 제거했습니다.

### `weathers`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 날씨 식별자 |
| `grid_id` | UUID | FK → `weather_grid.id` (ON DELETE RESTRICT), NOT NULL | 예보 위치 |
| `forecasted_at` | TIMESTAMPTZ | NOT NULL | 기상청 예보 발표 시각 |
| `forecast_at` | TIMESTAMPTZ | NOT NULL | 예보 대상 시각 |
| `sky_status` | VARCHAR(20) | NOT NULL | 하늘 상태 |
| `precipitation_type` | VARCHAR(20) | NOT NULL | 강수 형태 |
| `precipitation_amount` | DOUBLE PRECISION | NULL | 강수량 |
| `precipitation_probability` | DOUBLE PRECISION | NULL | 강수 확률 |
| `humidity_current` | DOUBLE PRECISION | NULL | 현재 습도 |
| `humidity_compared_to_day_before` | DOUBLE PRECISION | NULL | 전일 대비 습도 |
| `temperature_current` | DOUBLE PRECISION | NULL | 현재 기온 |
| `temperature_compared_to_day_before` | DOUBLE PRECISION | NULL | 전일 대비 기온 |
| `temperature_min` | DOUBLE PRECISION | NULL | 최저 기온 |
| `temperature_max` | DOUBLE PRECISION | NULL | 최고 기온 |
| `wind_speed` | DOUBLE PRECISION | NULL | 풍속 |
| `created_at` | TIMESTAMPTZ | NOT NULL | 수집 시각 |

> 실제 `Weather` 엔티티 기준입니다(`location_id` → `grid_id`로 참조 대상 변경).

---

## 3. 의상 도메인

### `clothes`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 의상 식별자 |
| `owner_id` | UUID | FK → `users.id` (ON DELETE RESTRICT), NOT NULL | 의상 소유 사용자 |
| `name` | VARCHAR(100) | NOT NULL, CHECK 공백 아님 | 의상 이름 |
| `image_url` | VARCHAR(500) | NULL | 의상 이미지 URL |
| `type` | VARCHAR(30) | NOT NULL | Swagger ClothesType enum |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |
| `deleted_at` | TIMESTAMPTZ | NULL | 논리 삭제 시각 |

### `clothes_attribute_definitions`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 의상 속성 정의 식별자 |
| `name` | VARCHAR(100) | NOT NULL, UNIQUE, CHECK 공백 아님 | 속성 이름 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |
| `deleted_at` | TIMESTAMPTZ | NULL | 논리 삭제 시각 |

### `attribute_selectable_values`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 선택값 식별자 |
| `definition_id` | UUID | FK → `clothes_attribute_definitions.id` (ON DELETE CASCADE), NOT NULL | 속성 정의 |
| `value` | VARCHAR(100) | NOT NULL, CHECK 공백 아님 | 선택 가능한 문자열 |
| `display_order` | INTEGER | NOT NULL, DEFAULT 0, CHECK >= 0 | 표시 순서 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |
| `deleted_at` | TIMESTAMPTZ | NULL | 논리 삭제 시각 |

### `clothes_attributes`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 의상 속성 식별자 |
| `clothes_id` | UUID | FK → `clothes.id` (ON DELETE CASCADE), NOT NULL | 대상 의상 |
| `definition_id` | UUID | FK → `clothes_attribute_definitions.id` (ON DELETE RESTRICT), NOT NULL | 속성 정의 |
| `value` | VARCHAR(100) | NOT NULL, CHECK 공백 아님 | 의상에 저장된 속성값 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |

---

## 4. 피드·소셜 도메인

### `feeds`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 피드 식별자 |
| `author_id` | UUID | FK → `users.id` (ON DELETE SET NULL), NULL | 작성자 |
| `weather_id` | UUID | FK → `weathers.id` (ON DELETE SET NULL), NULL | 원본 날씨 |
| `weather_snapshot` | JSONB | NOT NULL | 피드 작성 당시 날씨 정보 |
| `content` | TEXT | NOT NULL, CHECK 공백 아님 | 피드 본문 |
| `like_count` | BIGINT | NOT NULL, DEFAULT 0, CHECK >= 0 | 좋아요 수 |
| `comment_count` | INTEGER | NOT NULL, DEFAULT 0, CHECK >= 0 | 댓글 수 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 수정 시각 |
| `deleted_at` | TIMESTAMPTZ | NULL | 논리 삭제 시각 |

### `feed_clothes`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 피드·의상 연결 식별자 |
| `feed_id` | UUID | FK → `feeds.id` (ON DELETE CASCADE), NOT NULL | 피드 |
| `clothes_id` | UUID | FK → `clothes.id` (ON DELETE CASCADE), NOT NULL | 의상 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 연결 시각 |

### `feed_likes`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 좋아요 식별자 |
| `feed_id` | UUID | FK → `feeds.id` (ON DELETE CASCADE), NOT NULL | 대상 피드 |
| `user_id` | UUID | FK → `users.id` (ON DELETE SET NULL), NULL | 좋아요 사용자 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 좋아요 시각 |

### `comments`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 댓글 식별자 |
| `feed_id` | UUID | FK → `feeds.id` (ON DELETE CASCADE), NOT NULL | 대상 피드 |
| `author_id` | UUID | FK → `users.id` (ON DELETE SET NULL), NULL | 댓글 작성자 |
| `content` | TEXT | NOT NULL, CHECK 공백 아님 | 댓글 내용 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |

### `follows`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 팔로우 식별자 |
| `follower_id` | UUID | FK → `users.id` (ON DELETE CASCADE), NOT NULL | 팔로우하는 사용자 |
| `followee_id` | UUID | FK → `users.id` (ON DELETE CASCADE), NOT NULL | 팔로우받는 사용자 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |

---

## 5. DM·알림 도메인

### `direct_messages`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | DM 식별자 |
| `sender_id` | UUID | FK → `users.id` (ON DELETE SET NULL), NULL | 발신자 |
| `receiver_id` | UUID | FK → `users.id` (ON DELETE SET NULL), NULL | 수신자 |
| `dm_key` | VARCHAR(73) | NOT NULL, CHECK length = 73 | 두 사용자의 공통 대화 키 |
| `content` | TEXT | NOT NULL, CHECK 공백 아님 | 메시지 내용 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 전송 시각 |

> `sender_id`/`receiver_id`가 둘 다 NULL인 메시지는 배치로 정리 삭제 예정입니다.

### `notifications`

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `id` | UUID | PK | 알림 식별자 |
| `receiver_id` | UUID | FK → `users.id` (ON DELETE CASCADE), NOT NULL | 알림 수신자 |
| `title` | VARCHAR(255) | NOT NULL, CHECK 공백 아님 | 알림 제목 |
| `content` | TEXT | NOT NULL, CHECK 공백 아님 | 알림 본문 |
| `level` | VARCHAR(20) | NOT NULL, CHECK IN (`INFO`,`WARNING`,`ERROR`) | 알림 레벨 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 생성 시각 |

---

## 6. Spring Batch 메타데이터

`spring-batch-core:5.2.6` 공식 `schema-postgresql.sql`을 그대로 반영했습니다: `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_JOB_EXECUTION_PARAMS`, `BATCH_STEP_EXECUTION`, `BATCH_STEP_EXECUTION_CONTEXT`, `BATCH_JOB_EXECUTION_CONTEXT` 테이블과 `BATCH_STEP_EXECUTION_SEQ`, `BATCH_JOB_EXECUTION_SEQ`, `BATCH_JOB_SEQ` 시퀀스.

---

## 7. 익스텐션 및 인덱스

- `pg_trgm`: `clothes_attribute_definitions.name`, `feeds.content`의 트라이그램 GIN 인덱스(`idx_..._trgm`)에 사용
- 각 UUID PK는 `DEFAULT gen_random_uuid()`를 쓰지 않습니다. 모든 엔티티가 `GenerationType.UUID`로 앱에서 ID를 생성해 insert하므로 `pgcrypto` 익스텐션도 필요 없습니다.
- 그 외 `users`/`clothes`/`clothes_attribute_definitions`/`clothes_attributes`/`feeds`/`feed_clothes`/`feed_likes`/`comments`/`follows`/`direct_messages`/`notifications`에 조회 패턴 기반 인덱스 추가 (복합 UNIQUE 제약이 이미 커버하는 컬럼은 중복 생성하지 않음)

---

## 8. 버전 1 대비 변경점

### 전역
- `pg_trgm` 익스텐션 추가, `pgcrypto`/`gen_random_uuid()`는 쓰지 않음 (앱이 ID를 생성하는 구조와 일치)
- 조회용 `Indexes` 섹션 신규 추가
- Spring Batch 메타데이터 테이블 6개 + 시퀀스 3개 추가

### `users`
- `role`에 `DEFAULT 'USER'`, `CHECK IN ('ADMIN','USER')` 추가
- `created_at`/`updated_at`에 `DEFAULT now()` 추가

### `profiles`
- `location_id`(FK → locations) 제거 → `x`, `y`, `province`, `city`, `district` 인라인 컬럼으로 대체
- `profile_image_url` → `image_url`로 리네임, `VARCHAR(500)` → `TEXT`
- `latitude`/`longitude`: `DOUBLE PRECISION` → `NUMERIC(9,6)`
- `temperature_sensitivity` → `temp_sensitivity`로 리네임, `NOT NULL` → nullable, `CHECK BETWEEN 1 AND 5` 추가
- `gender`에 `CHECK IN ('MALE','FEMALE','OTHER')` 추가

### `oauth_accounts`
- `CHECK provider IN ('GOOGLE','KAKAO')` 추가
- `created_at`에 `DEFAULT now()` 추가
- `UNIQUE(user_id, provider)`는 유지 (변경 없음)

### `locations` → `weather_grid`, `weathers.location_id` → `grid_id`
- `locations` 테이블 제거, 실제 `Grid` 엔티티 기준 `weather_grid`로 대체 (`location_names`, `updated_at` 컬럼 없음)
- `weathers.location_id` → `grid_id`로 리네임, 참조 테이블도 `weather_grid`로 변경

### 의상 도메인 (`clothes`, `clothes_attribute_definitions`, `attribute_selectable_values`, `clothes_attributes`)
- `name`/`value` 컬럼에 공백 문자열 방지 `CHECK` 추가
- `attribute_selectable_values`에 `CHECK display_order >= 0` 추가 (엔티티의 `@Check` 어노테이션과 일치시킴 — 버전 1엔 누락돼 있었음)
- `created_at`/`updated_at`에 `DEFAULT now()` 추가

### `feeds`
- `author_id`: `NOT NULL` → nullable, FK `RESTRICT` → `SET NULL`
- `content`에 공백 방지 `CHECK` 추가
- `created_at`/`updated_at`에 `DEFAULT now()` 추가

### `feed_clothes`
- `clothes_id`: FK `RESTRICT` → `CASCADE`

### `feed_likes`
- `user_id`: `NOT NULL` → nullable, FK `RESTRICT` → `SET NULL`

### `comments`
- `author_id`: `NOT NULL` → nullable, FK `RESTRICT` → `SET NULL`
- `content`에 공백 방지 `CHECK` 추가

### `follows`
- `follower_id`/`followee_id`: FK `RESTRICT` → `CASCADE` (둘 다)

### `direct_messages`
- `sender_id`/`receiver_id`: `NOT NULL` → nullable, FK `RESTRICT` → `SET NULL` (실제 `DirectMessage` 엔티티의 `nullable=true`와 일치시킴)
- `CHECK length(dm_key) = 73` 추가
- `content`에 공백 방지 `CHECK` 추가

### `notifications`
- `receiver_id`: FK `RESTRICT` → `CASCADE`
- `level`/`title`/`content`에 각각 `CHECK` 추가
