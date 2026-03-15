-- V9: restaurants 테이블에 YouTube 조회수 및 쇼츠 제목 컬럼 추가
-- view_count  : YouTube 영상 조회수 (핫한 맛집 정렬용)
-- source_video_title: 맛집이 소개된 YouTube 쇼츠 제목

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS view_count BIGINT,
    ADD COLUMN IF NOT EXISTS source_video_title VARCHAR(500);

-- 조회수 내림차순 인덱스 (핫한 맛집 정렬 최적화)
CREATE INDEX IF NOT EXISTS idx_restaurants_view_count
    ON restaurants (view_count DESC NULLS LAST);
