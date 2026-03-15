-- Phase 2: 맛집 테이블 초기 생성
-- PostGIS 확장 활성화 (PostGIS 이미지에는 이미 설치되어 있으나 명시적 선언)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 맛집 테이블
CREATE TABLE restaurants (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    address       VARCHAR(500) NOT NULL,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    -- Phase 7에서 ST_DWithin 반경 검색에 사용 (GEOGRAPHY 타입: 구면 거리 계산)
    location      GEOGRAPHY(POINT, 4326),
    category      VARCHAR(50)  NOT NULL DEFAULT 'UNKNOWN',
    price_range   VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    rating        DOUBLE PRECISION,
    thumbnail_url VARCHAR(1000),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 맛집 태그 컬렉션 테이블 (JPA @ElementCollection 대응)
CREATE TABLE restaurant_tags (
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tag           VARCHAR(100) NOT NULL
);

-- GiST 인덱스 (ST_DWithin 공간 쿼리 성능 핵심) — Phase 7에서 활용
CREATE INDEX idx_restaurants_location ON restaurants USING GIST(location);
CREATE INDEX idx_restaurants_category ON restaurants (category);

-- latitude/longitude 변경 시 location 자동 업데이트 트리거
-- Phase 6.5 Kakao Geocoding 결과 저장 시 자동 반영
CREATE OR REPLACE FUNCTION fn_update_restaurant_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location = ST_SetSRID(
            ST_MakePoint(NEW.longitude, NEW.latitude),
            4326
        )::GEOGRAPHY;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_restaurants_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON restaurants
    FOR EACH ROW EXECUTE FUNCTION fn_update_restaurant_location();
