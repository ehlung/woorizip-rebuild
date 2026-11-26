-- 가입 이메일 인증, 가입 동의, 메일 발송 작업 (0단계)
-- 설계: docs/plans/auth.md "PR 2 설계", 인증 규칙: docs/adr/0009-auth-token.md, 메일: docs/adr/0018-email-delivery.md

-- 가입과 비밀번호 재설정에 쓰는 6자리 코드. 코드 원문은 저장하지 않고 해시만 둔다.
CREATE TABLE email_codes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purpose         VARCHAR(20)  NOT NULL CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET')),
    -- 소문자로 바꾼 이메일 주소
    email           VARCHAR(320) NOT NULL,
    -- SHA-256 hex
    code_hash       VARCHAR(64)  NOT NULL,
    -- 5회 틀리면 그 코드는 쓸 수 없다
    failed_attempts INTEGER      NOT NULL CHECK (failed_attempts >= 0),
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL
);

-- 이메일마다 가장 최근 코드로 확인한다.
CREATE INDEX email_codes_latest ON email_codes (purpose, email, created_at DESC);

-- 코드를 확인한 뒤 가입을 끝낼 때까지 쓰는 짧은 수명의 확인 값. 해시만 둔다.
CREATE TABLE signup_tickets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);

-- 가입 때 받은 동의. 약관 버전별로 남긴다.
CREATE TABLE user_consents (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    kind      VARCHAR(30) NOT NULL CHECK (kind IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'AI_DATA_TRANSFER', 'AGE_14_OR_OLDER')),
    version   VARCHAR(20) NOT NULL,
    agreed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, kind, version)
);

-- 보낼 메일 한 건. 요청 트랜잭션에서 저장하고 커밋 직후 보낸다. 실패하면 스케줄러가 다시 보낸다.
CREATE TABLE mail_jobs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind            VARCHAR(30)   NOT NULL CHECK (kind IN ('SIGNUP_CODE', 'SIGNUP_ALREADY_REGISTERED')),
    recipient       VARCHAR(320)  NOT NULL,
    subject         VARCHAR(200)  NOT NULL,
    -- 코드가 들어 있으므로 보낸 뒤나 만료되면 지운다
    body            VARCHAR(2000),
    status          VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        INTEGER       NOT NULL CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    -- 이 시각이 지나면 보내지 않는다 (코드 메일은 코드 만료 시각)
    expires_at      TIMESTAMPTZ   NOT NULL,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL,
    sent_at         TIMESTAMPTZ,
    CHECK (status = 'PENDING' OR body IS NULL)
);

-- 스케줄러가 보낼 차례인 작업을 찾는다.
CREATE INDEX mail_jobs_due ON mail_jobs (next_attempt_at) WHERE status = 'PENDING';

-- 같은 이메일로 보낸 횟수를 센다(요청 횟수 제한).
CREATE INDEX mail_jobs_by_recipient ON mail_jobs (recipient, created_at);
