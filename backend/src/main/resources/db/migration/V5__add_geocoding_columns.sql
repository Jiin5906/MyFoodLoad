-- Phase 6.5: 지오코딩 관련 컬럼 추가

-- 카카오 장소 고유 ID (중복 저장 방지용)
ALTER TABLE restaurants ADD COLUMN kakao_place_id VARCHAR(50);
ALTER TABLE restaurants ADD CONSTRAINT uq_restaurants_kakao_place_id UNIQUE (kakao_place_id);

-- 영상별 맛집 추출 완료 여부 (공유 캐시: 한 번 추출하면 다른 사용자도 재사용)
ALTER TABLE video_metadata ADD COLUMN restaurant_extracted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_video_metadata_not_extracted
    ON video_metadata (restaurant_extracted) WHERE restaurant_extracted = FALSE;
