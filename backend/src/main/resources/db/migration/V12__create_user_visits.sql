-- V12: 방문 완료 기록 테이블 (C-2)
-- 사용자가 "방문 완료" 체크한 맛집을 추천에서 제외하는 데 활용

CREATE TABLE user_visits (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    visited_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_visit UNIQUE (user_id, restaurant_id)
);

-- DB 레벨 NOT EXISTS 쿼리 최적화를 위한 복합 인덱스 (Gemini 지적 반영)
CREATE INDEX idx_user_visits_user_restaurant ON user_visits (user_id, restaurant_id);
