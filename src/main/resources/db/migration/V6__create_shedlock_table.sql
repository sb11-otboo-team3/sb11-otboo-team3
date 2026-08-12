-- ShedLock(JdbcTemplateLockProvider) 표준 스키마. 서버 여러 대에서 같은 @Scheduled 배치가
-- 동시에 중복 실행되는 것을 막기 위한 분산 락 테이블 - weather 정리/프리페치 배치부터 적용.
CREATE TABLE shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP   NOT NULL,
    locked_at  TIMESTAMP   NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
