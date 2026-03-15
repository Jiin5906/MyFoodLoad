-- Phase 6: 자막 컬럼 추가 + 사용자 음식 선호도 테이블 생성

-- video_metadata에 자막 저장 컬럼 추가
-- (공유 캐시: 최초 수집 후 다른 사용자도 재사용)
ALTER TABLE video_metadata ADD COLUMN transcript TEXT;

-- 사용자별 음식 선호도 프로파일 (LLM 분석 결과)
CREATE TABLE user_preferences (
    id                   BIGSERIAL    PRIMARY KEY,
    user_id              BIGINT       UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    price_range          VARCHAR(20),
    confidence           DOUBLE PRECISION,
    analyzed_video_count INT          NOT NULL DEFAULT 0,
    last_analyzed_at     TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 음식 태그 (tag + score)
CREATE TABLE user_food_tags (
    user_preference_id BIGINT       NOT NULL REFERENCES user_preferences(id) ON DELETE CASCADE,
    tag                VARCHAR(100) NOT NULL,
    score              DOUBLE PRECISION NOT NULL DEFAULT 1.0
);

-- 분위기 태그
CREATE TABLE user_ambiance_tags (
    user_preference_id BIGINT       NOT NULL REFERENCES user_preferences(id) ON DELETE CASCADE,
    tag                VARCHAR(100) NOT NULL
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences (user_id);
