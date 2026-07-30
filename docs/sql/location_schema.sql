-- Location(위경도 -> 행정구역) 캐시 테이블
-- 엔티티: com.otboo.domain.weather.entity.Location (+ com.otboo.global.common.entity.BaseEntity)

CREATE TABLE locations (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMP NOT NULL,
    x                  INT NOT NULL,
    y                  INT NOT NULL,
    province           VARCHAR(255) NOT NULL,
    city               VARCHAR(255) NOT NULL,
    district           VARCHAR(255) NOT NULL,
    last_requested_at  TIMESTAMP NOT NULL,
    -- 행정구역 유니크 키 (province, city, district) - Postgres에서 UNIQUE 제약은 자동으로 인덱스를 생성함
    CONSTRAINT uq_locations_province_city_district UNIQUE (province, city, district)
);

-- 3일간 미사용 시 삭제하는 정리 배치(예정, 아직 미구현)에서
-- last_requested_at < now() - interval '3 days' 조건으로 조회할 것을 대비한 인덱스
CREATE INDEX idx_locations_last_requested_at ON locations (last_requested_at);