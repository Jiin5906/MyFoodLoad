CREATE TABLE users (
    id                BIGSERIAL    PRIMARY KEY,
    google_id         VARCHAR(255) NOT NULL UNIQUE,
    email             VARCHAR(255) NOT NULL UNIQUE,
    name              VARCHAR(255) NOT NULL,
    profile_image_url TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_google_id ON users (google_id);
CREATE INDEX idx_users_email     ON users (email);
