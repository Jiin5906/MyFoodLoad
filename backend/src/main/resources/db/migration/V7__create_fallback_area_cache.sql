-- Phase 폴백 추천 캐시
-- YouTube API 중복 호출 방지를 위한 위치 격자 캐시 테이블.
-- lat_grid / lon_grid: 위도·경도를 소수점 2자리로 반올림한 격자 좌표 (≈ 1.1km × 1.1km)
-- cached_at: 마지막으로 YouTube API를 호출한 시각.
--            이후 7일 이내 동일 격자 요청은 DB 조회만 수행.

CREATE TABLE fallback_area_cache (
    lat_grid   DOUBLE PRECISION NOT NULL,
    lon_grid   DOUBLE PRECISION NOT NULL,
    cached_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (lat_grid, lon_grid)
);

COMMENT ON TABLE  fallback_area_cache            IS '폴백 추천 위치 격자 캐시 — YouTube API 호출 이력';
COMMENT ON COLUMN fallback_area_cache.lat_grid   IS '위도 소수점 2자리 반올림 (≈1.1km 격자)';
COMMENT ON COLUMN fallback_area_cache.lon_grid   IS '경도 소수점 2자리 반올림 (≈1.1km 격자)';
COMMENT ON COLUMN fallback_area_cache.cached_at  IS '마지막 YouTube API 호출 시각';
