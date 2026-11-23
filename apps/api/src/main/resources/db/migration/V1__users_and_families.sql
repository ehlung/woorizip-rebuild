-- 사용자, 로그인 수단, 가족, 구성원 (0단계)
-- 도메인 설명: docs/03-architecture.md 3장, 가족 소속 규칙: docs/adr/0010-database-and-schema.md

CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 가입 때 받은 이름. 가족에 들어갈 때 구성원 이름의 처음 값이 된다.
    name        VARCHAR(20) NOT NULL,
    -- 화면 크기 3단계 (ADR 0015). 기기에도 저장하지만 다른 기기에서도 유지되도록 계정에 둔다.
    screen_size VARCHAR(10) NOT NULL CHECK (screen_size IN ('SMALL', 'MEDIUM', 'LARGE')),
    created_at  TIMESTAMPTZ NOT NULL
);

-- 로그인 수단. 한 계정이 여러 수단을 가질 수 있다. 0단계는 이메일, 1단계에 카카오, 구글, 애플.
CREATE TABLE user_identities (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    provider      VARCHAR(10)  NOT NULL CHECK (provider IN ('EMAIL')),
    -- 수단 안에서 사용자를 구분하는 값. 이메일 로그인은 소문자로 바꾼 이메일 주소.
    subject       VARCHAR(320) NOT NULL,
    -- 이메일 로그인의 BCrypt 해시 (ADR 0009). 소셜 로그인은 비어 있다.
    password_hash VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL,
    UNIQUE (provider, subject),
    CHECK (provider <> 'EMAIL' OR password_hash IS NOT NULL)
);

CREATE INDEX user_identities_user_id ON user_identities (user_id);

CREATE TABLE families (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(20) NOT NULL,
    -- 헷갈리는 글자(0, O, 1, I, L)를 뺀 대문자와 숫자 8자리.
    invite_code VARCHAR(8)  NOT NULL UNIQUE CHECK (invite_code ~ '^[A-HJKMNP-Z2-9]{8}$'),
    created_at  TIMESTAMPTZ NOT NULL
);

-- 사용자의 가족 소속. 가족 안의 활동(답변, 반응)은 사용자가 아니라 구성원을 가리킨다 (ADR 0008).
CREATE TABLE members (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id BIGINT      NOT NULL REFERENCES families (id),
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    role      VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    -- 가족 안에서 보이는 이름과 호칭(엄마, 아빠, 직접 입력 등).
    name      VARCHAR(20) NOT NULL,
    relation  VARCHAR(20) NOT NULL,
    status    VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'LEFT')),
    joined_at TIMESTAMPTZ NOT NULL,
    left_at   TIMESTAMPTZ,
    CHECK ((status = 'LEFT') = (left_at IS NOT NULL))
);

-- 0단계: 한 사용자는 활성 가족 하나에만 속한다. 나간 기록은 남고, 다른 가족에 다시 들어갈 수 있다.
-- 3단계에 여러 가족 소속을 열 때 (family_id, user_id) 기준으로 바꾼다 (ADR 0010).
CREATE UNIQUE INDEX members_one_active_family_per_user ON members (user_id) WHERE status = 'ACTIVE';

-- 한 가족의 활성 방장은 한 명이다.
CREATE UNIQUE INDEX members_one_active_owner_per_family ON members (family_id) WHERE status = 'ACTIVE' AND role = 'OWNER';

-- 구성원 목록 조회.
CREATE INDEX members_active_by_family ON members (family_id) WHERE status = 'ACTIVE';
