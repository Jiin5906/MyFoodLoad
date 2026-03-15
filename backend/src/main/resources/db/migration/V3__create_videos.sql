-- Phase 5: YouTube 영상 메타데이터 테이블
-- Gemini 피드백 반영: is_analyzed 공유 캐시로 YouTube API·LLM Quota 절약

CREATE TABLE video_metadata (
    id            BIGSERIAL    PRIMARY KEY,
    video_id      VARCHAR(20)  UNIQUE NOT NULL,
    title         TEXT         NOT NULL,
    description   TEXT,
    channel_id    VARCHAR(30)  NOT NULL,
    channel_title VARCHAR(255),
    category_id   VARCHAR(10),
    published_at  TIMESTAMPTZ,
    thumbnail_url TEXT,
    is_analyzed   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 영상별 태그 (ElementCollection 매핑)
CREATE TABLE video_tags (
    video_metadata_id BIGINT       NOT NULL REFERENCES video_metadata(id) ON DELETE CASCADE,
    tag               VARCHAR(100) NOT NULL
);

-- 사용자-영상 좋아요 관계 (다대다)
CREATE TABLE user_video_likes (
    user_id  BIGINT      NOT NULL REFERENCES users(id)                     ON DELETE CASCADE,
    video_id VARCHAR(20) NOT NULL REFERENCES video_metadata(video_id)      ON DELETE CASCADE,
    liked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);

CREATE INDEX idx_video_metadata_video_id      ON video_metadata   (video_id);
-- 미분석 영상만 빠르게 조회하는 부분 인덱스 (Phase 6 LLM 파이프라인용)
CREATE INDEX idx_video_metadata_not_analyzed  ON video_metadata   (is_analyzed) WHERE is_analyzed = FALSE;
CREATE INDEX idx_user_video_likes_user_id     ON user_video_likes (user_id);
