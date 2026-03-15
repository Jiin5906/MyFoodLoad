-- V11: restaurants 테이블에 Gemini 추천 이유 추가 (B-4 스토리텔링)
-- recommendation_reason: 사용자의 유튜브 좋아요 기록 기반 추천 이유 (한 문장)

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS recommendation_reason VARCHAR(500);
