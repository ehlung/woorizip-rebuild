# 0016. 로컬 실행과 테스트 전략

- 상태: 채택
- 날짜: 2026-11-20
- 관련 문제: P-03, P-04, 01의 4장 ([01-legacy-analysis.md](../01-legacy-analysis.md))

## 맥락

기존 프로젝트는 실행하고 검증할 방법이 없었다.

- 전체를 띄우려면 MySQL, BE, AI, FE를 각각 다른 방식으로 준비해야 했고, BE는 저장소에 없는 설정 파일이 있어야 실행됐다. 통합 문제를 배포 직전에야 발견했다 (P-03)
- DB 스키마가 저장소에 없었다 (P-04)
- 자동 테스트는 BE의 `contextLoads` 하나뿐이었고, CI는 테스트를 건너뛰고 빌드했다. 로컬과 운영 설정이 달라 운영에서만 생기는 버그가 있었다 (01의 4장)

리빌드의 문제 정의 네 번째는 "언제든 같은 방법으로 실행하고 검증할 수 있다"이다([02-domain-and-problem.md](../02-domain-and-problem.md) 5장). [04-evaluation.md](../04-evaluation.md)의 assertion은 대부분 자동 테스트로 확인하기로 했다.

지금 저장소의 로컬 인프라는 Docker Compose로 PostgreSQL과 LocalStack(S3, SQS)을 띄우고, [ADR 0014](0014-secrets-and-config.md)도 로컬 개발을 Docker Compose와 LocalStack으로 흉내 낸다고 적었다. 그 뒤로 두 가지가 바뀌었다. 이 ADR이 채택되면 0014의 그 부분을 대체한다.

- [ADR 0002](0002-async-analysis.md)에서 메시지 큐를 쓰지 않기로 해 SQS가 필요 없다. 로컬에서 흉내 낼 AWS 서비스는 S3뿐이다.
- LocalStack은 2026-03-23부터 최신 이미지를 실행하려면 계정의 인증 토큰이 필요하다. 무료인 Hobby 플랜은 비상업적 사용에만 쓸 수 있다(https://www.localstack.cloud/pricing , https://blog.localstack.cloud/the-road-ahead-for-localstack/). 지금 저장소는 토큰 없이 실행되는 마지막 버전(4.14)에 고정해 두었고, 이 버전은 더 이상 업데이트되지 않는다.

## 판단 기준

1. 저장소를 받은 사람이 문서 한 곳을 보고 전체를 실행할 수 있다
2. 테스트가 운영과 같은 종류의 DB와 같은 동작에서 돌아간다
3. presigned URL의 조건, 만료처럼 S3에서만 확인할 수 있는 동작을 실제로 확인한다
4. 외부 서비스(AI 제공자, 푸시) 없이도 전체 흐름을 테스트할 수 있다
5. 로컬과 CI에 오래 사는 비밀값을 두지 않는다([ADR 0014](0014-secrets-and-config.md))
6. 1인 개발로 유지할 수 있고, 업데이트가 끊긴 도구에 기대지 않는다

## 후보

### 테스트 DB

| 후보 | 특징 |
| --- | --- |
| 메모리 DB (H2 등) | 빠르다. 조건이 붙은 인덱스, `SKIP LOCKED`, Flyway의 PostgreSQL 문법을 그대로 확인할 수 없다(기준 2) |
| Testcontainers의 PostgreSQL | 테스트마다 실제 PostgreSQL 컨테이너를 띄운다. 운영과 같은 DB에서 돌아간다 |

### 로컬과 테스트의 S3

#### A. LocalStack 4.14 고정 (지금)

- 장점: 이미 구성되어 있다.
- 단점: 업데이트와 보안 패치가 없다(기준 6).

#### B. LocalStack Hobby 플랜

- 장점: 최신 버전을 쓴다.
- 단점: 비상업적 사용에만 쓸 수 있어, 이 서비스를 출시한 뒤에는 조건이 맞지 않을 수 있다. 로컬과 CI에 인증 토큰이라는 비밀값이 하나 생긴다(기준 5).

#### C. S3 흉내 도구 (Adobe S3Mock 등)

Apache 2.0 라이선스이고, Testcontainers 연동(`s3mock-testcontainers`)을 제공한다(https://github.com/adobe/S3Mock).

- 장점: 비밀값 없이 빠르게 돈다. 파일 올리기, 내려받기, 존재 확인 같은 기본 동작을 테스트할 수 있다.
- 단점: presigned URL의 서명, 만료, 메서드를 검사하지 않는다("Presigned URLs: Accepted but not validated (expiration, signature, HTTP verb not checked)"). presigned POST의 크기 조건을 검사하는지는 문서에 없다. A-08, A-09를 이 도구로 확인할 수 없다(기준 3).

#### D. 실제 AWS의 개발용 S3 버킷

- 장점: presigned URL 조건, 만료, 수명 주기 규칙이 운영과 똑같이 동작한다(기준 3). 휴대폰에서 개발 중인 앱으로 업로드할 때도 인터넷 주소라 노트북의 사설 주소를 맞출 필요가 없다.
- 단점: 인터넷이 필요하다. AWS 자격 증명이 필요하다. 로컬은 짧게 사는 로그인 자격 증명(IAM Identity Center 등)으로, CI는 GitHub OIDC로 받는 임시 자격 증명으로 오래 사는 키 없이 쓸 수 있다.

### 외부 서비스

| 후보 | 특징 |
| --- | --- |
| 실제 제공자 호출 | 실제 결과를 본다. 테스트마다 비용이 들고 결과가 매번 달라 테스트가 흔들린다 |
| 가짜 구현 | 단계마다 인터페이스를 두기로 했으므로([ADR 0011](0011-ai-worker-framework.md)) 정해진 결과를 돌려주는 구현으로 바꿔 끼운다. 실제 품질은 평가셋(ADR 0013)으로 따로 본다 |

## 결정

**DB는 Testcontainers의 PostgreSQL로, S3는 기본 동작은 C(S3Mock)로 테스트하고 S3 고유 동작은 D(개발용 버킷)에서 확인한다. 외부 AI와 푸시는 가짜 구현으로 테스트한다. LocalStack은 쓰지 않는다.**

### 로컬 실행

| 구성 요소 | 로컬에서 |
| --- | --- |
| PostgreSQL | Docker Compose. 운영과 같은 메이저 버전 |
| S3 | 개발용 S3 버킷. 개인 자격 증명은 짧게 사는 로그인으로 받는다 |
| API 서버 | 직접 실행(`local` 프로필) 또는 Compose의 `full` 프로필로 컨테이너 실행 |
| AI 워커 | 직접 실행 또는 Compose의 `full` 프로필. 외부 AI는 설정으로 가짜 구현과 실제 제공자 중 고른다 |
| 앱 | Expo 개발 서버. API 주소는 `.env.local`에서 읽는다(0014) |
| 푸시 | 기본은 가짜 발송(보낸 기록만 남김). 실기기 확인 때만 Expo 푸시 서비스로 보낸다 |

- 한 명령으로 PostgreSQL, API 서버, AI 워커(가짜 AI)를 함께 띄우는 Compose `full` 프로필을 둔다(P-03).
- 실행 방법은 루트 README 한 곳에 적는다. 필요한 설정 이름은 `.env.example`에 둔다(0014).
- 질문 은행처럼 모든 환경에 같아야 하는 데이터는 Flyway 마이그레이션으로 넣는다. 개발용 예시 가족, 답변 같은 데이터는 개발용 명령으로만 넣고, 운영 설정에서는 실행할 수 없게 한다([ADR 0005](0005-service-communication.md)의 원칙 4).
- Java, Node, Python 버전은 각 빌드 도구에 고정한다(Gradle toolchain, `package.json`의 `engines`, 0011의 잠금 파일).

### 테스트 층

| 층 | 대상 | 도구 | 실행 |
| --- | --- | --- | --- |
| 단위 | 도메인 규칙, 분석 단계, 공통 로직 | JUnit 5, pytest, Jest | 모든 PR |
| 통합 | API와 DB(권한, 작업 표, 마이그레이션, 쿼리 수), 기본 S3 동작 | Testcontainers(PostgreSQL, S3Mock) | 모든 PR |
| 명세 | 생성 클라이언트가 서버 명세와 같은가 | 클라이언트 재생성 후 비교(0006) | 모든 PR |
| 흐름 | 업로드 완료 → 워커(가짜 AI) → 답변 READY, 실패와 재시도 | Compose `full`, 시나리오 스크립트 | 모든 PR |
| S3 고유 동작 | presigned 조건과 만료(A-08, A-09), 수명 주기 | 개발용 버킷, GitHub OIDC | `main` 병합 후, 매일 |
| 앱 화면 | 핵심 흐름(가입 → 촬영 → 업로드) | Maestro | 테스트 배포 전 로컬에서 |
| 실기기 | 녹화 형식, 백그라운드 업로드, 푸시(0007), 화면 크기(0015) | 확인 목록 | 테스트 배포 전 |

- 테스트 DB에 메모리 DB를 쓰지 않는다.
- 0008의 권한 테스트는 등록된 API 목록을 자동으로 순회한다.
- 실제 AI 제공자 호출은 테스트가 아니라 평가(ADR 0013)에서 한다.

### CI

- GitHub Actions에서 바뀐 앱만 빌드하고 테스트한다.
- 병합 조건: 단위, 통합, 명세, 흐름 테스트와 gitleaks(0014)가 모두 통과해야 한다. 테스트를 건너뛰는 빌드는 두지 않는다.
- AWS에 접근하는 작업은 GitHub OIDC로 받은 임시 자격 증명만 쓴다. 저장소와 CI 비밀값에 AWS 키를 두지 않는다.

### 버린 후보

- 메모리 DB는 조건이 붙은 인덱스와 `SKIP LOCKED`를 확인할 수 없다. 이 둘은 0002와 0010의 핵심이다.
- A는 업데이트가 끊긴 도구에 기댄다. B는 비상업적 조건과 토큰 비밀값이 문제다.
- C만 쓰면 presigned URL 조건이 실제로 막히는지 확인하지 못한 채 통과한다. 그래서 C는 기본 동작에만 쓰고 S3 고유 동작은 D에서 확인한다.

## 결과

- 얻는 것: 테스트가 운영과 같은 DB에서 돈다. presigned URL 조건을 실제 S3에서 확인한다. 외부 서비스 없이 전체 흐름을 테스트한다. 업데이트가 끊긴 도구와 오래 사는 키가 없다.
- 잃는 것: 로컬 개발에 인터넷과 AWS 로그인이 필요하다. S3 고유 동작은 PR마다가 아니라 병합 후에 확인한다. 앱 화면 테스트는 CI에서 돌리지 않는다.
- 후속 작업:
  - `infra/`에서 LocalStack과 SQS 설정을 지우고, Compose에 `full` 프로필을 추가한다
  - 개발용 버킷과 GitHub OIDC 역할을 만든다. AWS 계정 구성은 0017과 함께 정한다
  - 앱 화면 테스트를 CI에서 돌릴지는 테스트 배포 뒤 다시 판단한다

## 검증

assertion:

- 새로 받은 저장소에서 README의 명령만으로 PostgreSQL, API 서버, AI 워커를 띄우고 흐름 테스트를 통과한다.
- 통합 테스트는 PostgreSQL 컨테이너에서 Flyway 마이그레이션을 적용한 DB로 돈다.
- 흐름 테스트에서 업로드 완료부터 답변 READY까지 외부 AI 없이 통과한다.
- 저장소와 CI 설정에 AWS 액세스 키가 없다.
- 운영 설정에서는 개발용 예시 데이터 명령을 실행할 수 없다.
