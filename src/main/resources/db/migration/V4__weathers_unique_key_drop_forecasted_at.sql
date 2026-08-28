-- weathers 유니크 제약을 (grid_id, forecast_at, forecasted_at)에서 (grid_id, forecast_at)로 변경.
-- 같은 시간대(grid_id, forecast_at)를 여러 배치(forecasted_at)가 다시 예측해도 row를 새로 쌓지 않고,
-- upsert로 기존 row를 최신 값으로 덮어쓰는 구조로 바뀜에 따른 변경. forecasted_at은 이제
-- "이 row를 마지막으로 갱신한 배치가 언제 발표됐는지"만 나타내는 일반 컬럼.
--
-- 참고: CREATE INDEX CONCURRENTLY로 락 없이 만드는 방법도 검토했으나, Flyway가 마이그레이션 동안
-- 자체적으로 잠금용 트랜잭션을 계속 붙잡고 있어서 CONCURRENTLY가 그 트랜잭션이 끝나길 기다리다
-- 서로 맞물려 영원히 안 끝나는 문제가 있었다(Flyway+PostgreSQL의 알려진 제약). 지금은 운영 데이터
-- 규모가 작아 짧은 잠금이 문제되지 않는 단계라, 아래처럼 일반적인 방식(ADD CONSTRAINT ... UNIQUE)을
-- 그대로 쓴다 - 데이터가 커지면 그때 인덱스 생성을 Flyway 밖에서 수동으로 분리하는 걸 재검토할 것.

-- 1. 기존 데이터 중 (grid_id, forecast_at) 조합이 중복된 row 정리 - 발표시각(forecasted_at)이 가장
--    최신인 row만 남기고 나머지는 삭제(더 오래된 예측이라 최신 값으로 대체된 것으로 간주).
--    forecasted_at까지 같으면 id로 동점 처리(둘 중 하나만 남겨야 하므로).
DELETE FROM weathers w
USING weathers dup
WHERE w.grid_id = dup.grid_id
  AND w.forecast_at = dup.forecast_at
  AND (
    w.forecasted_at < dup.forecasted_at
    OR (w.forecasted_at = dup.forecasted_at AND w.id < dup.id)
  );

-- 2. 유니크 제약 교체
ALTER TABLE weathers DROP CONSTRAINT IF EXISTS uq_weathers_grid_forecast;
ALTER TABLE weathers ADD CONSTRAINT uq_weathers_grid_forecast UNIQUE (grid_id, forecast_at);
