-- 옷장을 부탁해 통합 스키마 (docs/erd/README.md 기준)

-- ============================================================
-- Extensions
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 1. 사용자·인증 도메인
-- ============================================================

CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    email              VARCHAR(320) NOT NULL,
    name               VARCHAR(100) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    role               VARCHAR(20) NOT NULL DEFAULT 'USER',
    locked             BOOLEAN NOT NULL DEFAULT FALSE,
    token_version      BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'USER'))
);

-- ============================================================
-- 2. 위치·날씨 도메인
-- ============================================================

CREATE TABLE weather_grid (
    id                 UUID PRIMARY KEY,
    x                  INTEGER      NOT NULL,
    y                  INTEGER      NOT NULL,
    last_requested_at  TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_weather_grid_x_y UNIQUE (x, y)
);

CREATE TABLE profiles (
    user_id            UUID PRIMARY KEY REFERENCES users (id) ON DELETE RESTRICT,
    image_url          TEXT,
    gender             VARCHAR(20),
    birth_date         DATE,
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    x                  INTEGER,
    y                  INTEGER,
    province           VARCHAR(50),
    city               VARCHAR(50),
    district           VARCHAR(50),
    temp_sensitivity   INTEGER,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_profiles_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_profiles_temp_sensitivity CHECK (temp_sensitivity BETWEEN 1 AND 5)
);

CREATE TABLE oauth_accounts (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    provider           VARCHAR(20) NOT NULL,
    provider_user_id   VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_oauth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_oauth_user_provider UNIQUE (user_id, provider),
    CONSTRAINT chk_oauth_provider CHECK (provider IN ('GOOGLE', 'KAKAO'))
);

CREATE TABLE weathers (
    id                                    UUID PRIMARY KEY,
    grid_id                               UUID              NOT NULL REFERENCES weather_grid (id) ON DELETE RESTRICT,
    forecasted_at                         TIMESTAMPTZ       NOT NULL,
    forecast_at                           TIMESTAMPTZ       NOT NULL,
    sky_status                            VARCHAR(20)       NOT NULL,
    precipitation_type                    VARCHAR(20)       NOT NULL,
    precipitation_amount                  DOUBLE PRECISION  NULL,
    precipitation_probability             DOUBLE PRECISION  NULL,
    humidity_current                      DOUBLE PRECISION  NULL,
    humidity_compared_to_day_before       DOUBLE PRECISION  NULL,
    temperature_current                   DOUBLE PRECISION  NULL,
    temperature_compared_to_day_before    DOUBLE PRECISION  NULL,
    temperature_min                       DOUBLE PRECISION  NULL,
    temperature_max                       DOUBLE PRECISION  NULL,
    wind_speed                            DOUBLE PRECISION  NULL,
    created_at                            TIMESTAMPTZ       NOT NULL,

    CONSTRAINT uq_weathers_grid_forecast UNIQUE (grid_id, forecast_at, forecasted_at)
);

-- ============================================================
-- 3. 의상 도메인
-- ============================================================

CREATE TABLE clothes_attribute_definitions (
    id                 UUID NOT NULL PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT uk_clothes_attribute_definitions_name UNIQUE (name),
    CONSTRAINT ck_clothes_attribute_definitions_name CHECK (length(trim(name)) > 0)
);

CREATE TABLE clothes (
    id                 UUID NOT NULL PRIMARY KEY,
    owner_id           UUID NOT NULL,
    name               VARCHAR(100) NOT NULL,
    image_url          VARCHAR(500),
    type               VARCHAR(30) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT fk_clothes_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_clothes_name CHECK (length(trim(name)) > 0)
);

CREATE TABLE attribute_selectable_values (
    id                 UUID NOT NULL PRIMARY KEY,
    definition_id      UUID NOT NULL,
    value              VARCHAR(100) NOT NULL,
    display_order      INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT uk_attribute_selectable_values_definition_value UNIQUE (definition_id, value),
    CONSTRAINT fk_attribute_selectable_values_definition FOREIGN KEY (definition_id) REFERENCES clothes_attribute_definitions(id) ON DELETE CASCADE,
    CONSTRAINT ck_attribute_selectable_values_value CHECK (length(trim(value)) > 0),
    CONSTRAINT ck_attribute_selectable_values_display_order CHECK (display_order >= 0)
);

CREATE TABLE clothes_attributes (
    id                 UUID NOT NULL PRIMARY KEY,
    clothes_id         UUID NOT NULL,
    definition_id      UUID NOT NULL,
    value              VARCHAR(100) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_clothes_attributes_clothes_definition UNIQUE (clothes_id, definition_id),
    CONSTRAINT fk_clothes_attributes_clothes FOREIGN KEY (clothes_id) REFERENCES clothes(id) ON DELETE CASCADE,
    CONSTRAINT fk_clothes_attributes_definition FOREIGN KEY (definition_id) REFERENCES clothes_attribute_definitions(id) ON DELETE RESTRICT,
    CONSTRAINT ck_clothes_attributes_value CHECK (length(trim(value)) > 0)
);

-- ============================================================
-- 4. 피드·소셜 도메인
-- ============================================================

CREATE TABLE feeds (
    id                 UUID NOT NULL PRIMARY KEY,
    author_id          UUID,
    weather_id         UUID,
    weather_snapshot   JSONB NOT NULL,
    content            TEXT NOT NULL,
    like_count         BIGINT NOT NULL DEFAULT 0,
    comment_count      INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT fk_feeds_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_feeds_weather FOREIGN KEY (weather_id) REFERENCES weathers(id) ON DELETE SET NULL,
    CONSTRAINT ck_feeds_like_count CHECK (like_count >= 0),
    CONSTRAINT ck_feeds_comment_count CHECK (comment_count >= 0),
    CONSTRAINT ck_feeds_content CHECK (length(trim(content)) > 0)
);

CREATE TABLE feed_clothes (
    id                 UUID NOT NULL PRIMARY KEY,
    feed_id            UUID NOT NULL,
    clothes_id         UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_feed_clothes_feed_clothes UNIQUE (feed_id, clothes_id),
    CONSTRAINT fk_feed_clothes_feed FOREIGN KEY (feed_id) REFERENCES feeds(id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_clothes_clothes FOREIGN KEY (clothes_id) REFERENCES clothes(id) ON DELETE CASCADE
);

CREATE TABLE feed_likes (
    id                 UUID NOT NULL PRIMARY KEY,
    feed_id            UUID NOT NULL,
    user_id            UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_feed_likes_feed_user UNIQUE (feed_id, user_id),
    CONSTRAINT fk_feed_likes_feed FOREIGN KEY (feed_id) REFERENCES feeds(id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE comments (
    id                 UUID NOT NULL PRIMARY KEY,
    feed_id            UUID NOT NULL,
    author_id          UUID,
    content            TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_comments_feed FOREIGN KEY (feed_id) REFERENCES feeds(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_comments_content CHECK (length(trim(content)) > 0)
);

CREATE TABLE follows (
    id                 UUID NOT NULL PRIMARY KEY,
    follower_id        UUID NOT NULL,
    followee_id        UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_follows_follower_followee UNIQUE (follower_id, followee_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
);

-- ============================================================
-- 5. DM·알림 도메인
-- ============================================================

CREATE TABLE direct_messages (
    id                 UUID NOT NULL PRIMARY KEY,
    sender_id          UUID,
    receiver_id        UUID,
    dm_key             VARCHAR(73) NOT NULL,
    content            TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_direct_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_direct_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_direct_messages_not_self CHECK (sender_id <> receiver_id),
    CONSTRAINT ck_direct_messages_content CHECK (length(trim(content)) > 0),
    CONSTRAINT ck_direct_messages_dm_key_length CHECK (length(dm_key) = 73)
);

CREATE TABLE notifications (
    id                 UUID NOT NULL PRIMARY KEY,
    receiver_id        UUID NOT NULL,
    title              VARCHAR(255) NOT NULL,
    content            TEXT NOT NULL,
    level              VARCHAR(20) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_level CHECK (level IN ('INFO', 'WARNING', 'ERROR')),
    CONSTRAINT ck_notifications_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_notifications_content CHECK (length(trim(content)) > 0)
);


-- ============================================
-- Indexes
-- ============================================

-- users
CREATE INDEX idx_users_email_id ON users(email, id);
CREATE INDEX idx_users_created_at_id ON users(created_at, id);

-- clothes
CREATE INDEX idx_clothes_owner_id ON clothes(owner_id);
CREATE INDEX idx_clothes_deleted_at ON clothes(deleted_at);
CREATE INDEX idx_clothes_type ON clothes(type);
CREATE INDEX idx_clothes_created_at_id ON clothes(created_at, id);

-- clothes_attribute_definitions
CREATE INDEX idx_clothes_attribute_definitions_deleted_at ON clothes_attribute_definitions(deleted_at);
CREATE INDEX idx_clothes_attribute_definitions_created_at_id ON clothes_attribute_definitions(created_at, id);
CREATE INDEX idx_clothes_attribute_definitions_name_trgm ON clothes_attribute_definitions USING GIN (name gin_trgm_ops);

-- clothes_attributes
CREATE INDEX idx_clothes_attributes_definition_id ON clothes_attributes(definition_id);

-- feeds
CREATE INDEX idx_feeds_deleted_at ON feeds(deleted_at);
CREATE INDEX idx_feeds_author_id ON feeds(author_id);
CREATE INDEX idx_feeds_weather_id ON feeds(weather_id);
CREATE INDEX idx_feeds_created_at_id ON feeds(created_at, id);
CREATE INDEX idx_feeds_like_count_id ON feeds(like_count, id);
CREATE INDEX idx_feeds_content_trgm ON feeds USING GIN (content gin_trgm_ops);

-- feed_clothes
CREATE INDEX idx_feed_clothes_clothes_id ON feed_clothes(clothes_id);

-- feed_likes
CREATE INDEX idx_feed_likes_user_id ON feed_likes(user_id);

-- comments
CREATE INDEX idx_comments_feed_created_at_id ON comments(feed_id, created_at, id);
CREATE INDEX idx_comments_author_id ON comments(author_id);

-- follows
CREATE INDEX idx_follows_followee_id ON follows(followee_id);

-- direct_messages
CREATE INDEX idx_direct_messages_dm_key_created_at_id ON direct_messages(dm_key, created_at, id);
CREATE INDEX idx_direct_messages_sender_id ON direct_messages(sender_id);
CREATE INDEX idx_direct_messages_receiver_id ON direct_messages(receiver_id);

-- notifications
CREATE INDEX idx_notifications_receiver_created_at_id ON notifications(receiver_id, created_at, id);


-- ============================================================
-- 6. Spring Batch 메타데이터 (spring-batch-core:5.2.6 공식 schema-postgresql.sql)
-- ============================================================

CREATE TABLE BATCH_JOB_INSTANCE  (
	JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT ,
	JOB_NAME VARCHAR(100) NOT NULL,
	JOB_KEY VARCHAR(32) NOT NULL,
	constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ;

CREATE TABLE BATCH_JOB_EXECUTION  (
	JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT  ,
	JOB_INSTANCE_ID BIGINT NOT NULL,
	CREATE_TIME TIMESTAMP NOT NULL,
	START_TIME TIMESTAMP DEFAULT NULL ,
	END_TIME TIMESTAMP DEFAULT NULL ,
	STATUS VARCHAR(10) ,
	EXIT_CODE VARCHAR(2500) ,
	EXIT_MESSAGE VARCHAR(2500) ,
	LAST_UPDATED TIMESTAMP,
	constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
	references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ;

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS  (
	JOB_EXECUTION_ID BIGINT NOT NULL ,
	PARAMETER_NAME VARCHAR(100) NOT NULL ,
	PARAMETER_TYPE VARCHAR(100) NOT NULL ,
	PARAMETER_VALUE VARCHAR(2500) ,
	IDENTIFYING CHAR(1) NOT NULL ,
	constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;

CREATE TABLE BATCH_STEP_EXECUTION  (
	STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT NOT NULL,
	STEP_NAME VARCHAR(100) NOT NULL,
	JOB_EXECUTION_ID BIGINT NOT NULL,
	CREATE_TIME TIMESTAMP NOT NULL,
	START_TIME TIMESTAMP DEFAULT NULL ,
	END_TIME TIMESTAMP DEFAULT NULL ,
	STATUS VARCHAR(10) ,
	COMMIT_COUNT BIGINT ,
	READ_COUNT BIGINT ,
	FILTER_COUNT BIGINT ,
	WRITE_COUNT BIGINT ,
	READ_SKIP_COUNT BIGINT ,
	WRITE_SKIP_COUNT BIGINT ,
	PROCESS_SKIP_COUNT BIGINT ,
	ROLLBACK_COUNT BIGINT ,
	EXIT_CODE VARCHAR(2500) ,
	EXIT_MESSAGE VARCHAR(2500) ,
	LAST_UPDATED TIMESTAMP,
	constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT  (
	STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT ,
	constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
	references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ;

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT  (
	JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT ,
	constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
