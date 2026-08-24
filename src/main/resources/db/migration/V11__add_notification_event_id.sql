-- Kafka 알림 소비 멱등성 보장을 위한 event_id 추가

-- 기존 알림 데이터가 있으므로 먼저 nullable 컬럼으로 추가
ALTER TABLE notifications
    ADD COLUMN event_id UUID;

-- 기존 알림은 과거 Kafka eventId가 없으므로
-- 이미 고유한 notification id를 legacy eventId로 사용
UPDATE notifications
SET event_id = id
WHERE event_id IS NULL;

-- backfill 이후 신규 알림은 반드시 eventId를 가져야 함
ALTER TABLE notifications
    ALTER COLUMN event_id SET NOT NULL;

-- 동일 Kafka 이벤트의 중복 저장을 DB 레벨에서 최종 차단
ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_event_id UNIQUE (event_id);