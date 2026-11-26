-- refresh token과 로그인 실패 기록 (0단계)
-- 설계: docs/plans/auth.md "PR 3 설계", 규칙: docs/adr/0009-auth-token.md

-- refresh token. 원문은 앱에만 주고 해시만 둔다. 쓸 때마다 새것으로 바꾼다(rotation).
CREATE TABLE refresh_tokens (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users (id),
    -- 한 번 로그인한 기기에서 이어지는 토큰 묶음. 로그아웃은 이 묶음을 폐기한다.
    session_id         UUID        NOT NULL,
    -- SHA-256 hex
    token_hash         VARCHAR(64) NOT NULL UNIQUE,
    -- 세션을 시작한 시각. 이로부터 1년이 지나면 다시 로그인해야 한다.
    session_started_at TIMESTAMPTZ NOT NULL,
    -- 발급 후 30일. 세션 시작 후 1년을 넘지 않는다.
    expires_at         TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    -- 새 토큰으로 바꾼 시각. 이 토큰이 다시 오면 탈취로 보고 사용자의 토큰을 모두 폐기한다.
    rotated_at         TIMESTAMPTZ,
    -- 로그아웃, 탈취 의심, 비밀번호 재설정으로 폐기한 시각
    revoked_at         TIMESTAMPTZ
);

CREATE INDEX refresh_tokens_active_by_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX refresh_tokens_by_session ON refresh_tokens (session_id);

-- 같은 이메일의 연속 로그인 실패. 가입하지 않은 이메일도 같은 규칙으로 센다(A-47).
CREATE TABLE login_failures (
    -- 소문자로 바꾼 이메일 주소
    email                VARCHAR(320) PRIMARY KEY,
    consecutive_failures INTEGER      NOT NULL CHECK (consecutive_failures > 0),
    locked_until         TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ  NOT NULL
);
