-- Phase 9: 맛집이 추출된 원본 YouTube 영상 ID 저장
-- 상세 화면에서 관련 Shorts 재생에 활용
ALTER TABLE restaurants ADD COLUMN source_video_id VARCHAR(20);
