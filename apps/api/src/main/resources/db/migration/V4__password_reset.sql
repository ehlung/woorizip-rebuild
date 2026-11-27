-- 비밀번호 재설정 (0단계)
-- 설계: docs/plans/auth.md "PR 4 설계", 규칙: docs/adr/0009-auth-token.md

-- 재설정 코드를 확인한 뒤 새 비밀번호를 정할 때까지 쓰는 짧은 수명의 확인 값. 해시만 둔다.
-- 가입 여부를 숨기려고 가입하지 않은 이메일도 같은 흐름을 타므로, 사용자가 아니라 이메일로 둔다.
CREATE TABLE password_reset_tickets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 소문자로 바꾼 이메일 주소
    email      VARCHAR(320) NOT NULL,
    -- SHA-256 hex
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX password_reset_tickets_by_email ON password_reset_tickets (email) WHERE used_at IS NULL;

-- 재설정 코드 메일과 비밀번호 변경 안내 메일을 더한다.
ALTER TABLE mail_jobs DROP CONSTRAINT mail_jobs_kind_check;
ALTER TABLE mail_jobs ADD CONSTRAINT mail_jobs_kind_check
    CHECK (kind IN ('SIGNUP_CODE', 'SIGNUP_ALREADY_REGISTERED', 'PASSWORD_RESET_CODE', 'PASSWORD_CHANGED'));

-- 재설정 코드 요청 횟수는 email_codes의 코드 수로 센다. V2의 email_codes_latest 인덱스를 쓴다.
