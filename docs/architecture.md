# 우리.zip 리빌드 아키텍처

## 1. 목표 아키텍처

```txt
[클라이언트]
Expo App / Web App -> Spring API
  (공유: design-tokens, api-client, 공통 로직 패키지)

[일반 요청]
Expo App -> Spring API -> PostgreSQL

[영상 업로드]
Expo App -> Spring API          : presigned URL 요청 (VideoAnswer UPLOADING 생성)
Expo App -> S3                  : 영상 직접 업로드
Expo App -> Spring API          : 업로드 완료 등록
Spring API -> PostgreSQL        : VideoAnswer UPLOADED + Outbox 이벤트 저장 (단일 트랜잭션)

[분석 요청 발행]
Outbox Relay -> SQS analysis-jobs

[AI 분석]
AI Worker <- SQS analysis-jobs
-> S3에서 영상 다운로드
-> TRANSCRIBE -> SUMMARIZE -> THUMBNAIL -> EMBED
-> 썸네일을 S3에 업로드
-> SQS analysis-results로 결과 발행

[결과 반영]
Spring API <- SQS analysis-results
-> 분석 결과와 상태를 멱등하게 DB 반영

[조회]
Expo App -> Spring API : 분석 상태 polling, 아카이브/검색 조회
S3 -> CloudFront -> Expo App : 영상/썸네일 조회
```

이 구조의 핵심 원칙은 세 가지다.

1. **업로드, 메타데이터 저장, AI 분석, 미디어 조회를 분리한다.** 영상과 AI 처리는 오래 걸리고 실패할 수 있으므로 앱 요청 안에서 동기적으로 처리하지 않는다.
2. **외부 진입점은 Spring API 하나다.** 모바일 앱과 웹은 AI 서버를 직접 호출하지 않는다. AI 서버는 내부망에만 노출한다.
3. **DB는 Spring API만 쓴다.** AI worker는 DB에 접근하지 않고 결과를 메시지로 보낸다.

## 2. 기술 스택

| 영역          | 스택                                                         |
| ------------- | ------------------------------------------------------------ |
| Mobile App    | Expo, React Native, TypeScript                               |
| Web App       | React, TypeScript (프레임워크는 웹 구축 단계에서 결정)       |
| Design System | 플랫폼 중립 디자인 토큰, 플랫폼별 공통 컴포넌트, Storybook   |
| API Client    | OpenAPI 스펙 기반 TypeScript 클라이언트 자동 생성            |
| Server State  | TanStack Query                                               |
| Validation    | Zod                                                          |
| FE Test       | Jest, React Native Testing Library, Maestro, Playwright      |
| FE Monorepo   | pnpm workspace                                               |
| API Server    | Spring Boot 3, Java 21, Gradle                               |
| Security      | Spring Security, JWT (access/refresh)                        |
| Persistence   | Spring Data JPA, QueryDSL, Flyway                            |
| Database      | PostgreSQL, pgvector                                         |
| AI Server     | FastAPI, Python                                              |
| AI Models     | faster-whisper, LLM(제공자 추상화), MediaPipe, OpenCV, 임베딩 모델 |
| File Storage  | AWS S3                                                       |
| CDN           | AWS CloudFront                                               |
| Queue         | AWS SQS (`analysis-jobs`, `analysis-results`, DLQ)           |
| Test          | JUnit5, Testcontainers, LocalStack, pytest, k6               |
| API Docs      | springdoc-openapi                                            |
| Monitoring    | Spring Actuator, Micrometer, CloudWatch, Sentry              |
| CI            | GitHub Actions                                               |
| Local Dev     | Docker Compose                                               |
| App Build     | Expo EAS                                                     |
| Repository    | 개인 모노레포                                                |

## 3. 스택 선택 이유

### Expo React Native

우리.zip은 모바일 중심 서비스이므로 웹앱보다 네이티브 앱이 서비스 성격에 더 적합하다.

- 카메라, 파일, 미디어, 알림 등 네이티브 기능을 일관된 API로 사용 가능
- EAS Build로 Android/iOS 빌드와 배포 파이프라인을 구성 가능
- 순수 React Native 대비 초기 설정과 네이티브 빌드 관리 부담이 적음
- TypeScript와 React 생태계를 웹 클라이언트와 공유 가능

### 모바일/웹 분리와 공유 패키지

모바일 앱과 웹은 하나의 코드베이스로 합치지 않고 별도 앱으로 만든다. 우리.zip에서 모바일은 촬영과 답변 중심, 웹은 큰 화면에서의 아카이브 열람 중심으로 사용 흐름이 다르기 때문이다. React Native Web처럼 한 UI 코드로 두 플랫폼을 맞추면, 어느 한쪽의 사용 경험이 다른 쪽에 맞춰 타협되기 쉽다.

대신 플랫폼과 무관한 부분은 패키지로 공유한다.

- 디자인 토큰: 두 플랫폼이 같은 시각 언어를 유지
- API 클라이언트: 서버 계약 변경이 두 클라이언트에 동시에 타입 오류로 드러남
- 공통 로직: 입력 검증 스키마, 도메인 상수, 포맷터, 쿼리 키

### TanStack Query

서버에서 오는 데이터(가족, 질문, 답변, 분석 상태)는 클라이언트 전역 상태와 분리해 서버 상태로 관리한다.

- 캐싱, 재요청, 무효화 규칙을 한곳에서 관리
- 분석 상태 polling을 상태에 따라 시작/중단하기 쉬움
- 댓글/반응의 낙관적 업데이트와 실패 시 롤백 지원
- 모바일과 웹에서 같은 방식으로 사용 가능

### Spring Boot

백엔드는 기존 팀 프로젝트와 같은 Spring Boot를 사용한다. 스택을 바꾸기보다, 기존 구현에서 드러난 구조적 한계를 같은 스택 위에서 개선하는 데 집중하기 위해서다.

기존 구현과 달라지는 점은 다음과 같다.

- 기능 단위가 아닌 도메인 단위 패키지 구조
- 가족 멤버십 기반 인가를 공통 계층에서 처리
- AI 서버 동기 호출 대신 Outbox와 SQS 기반 비동기 처리
- Flyway로 스키마 변경 이력 관리
- Testcontainers와 LocalStack으로 실제 PostgreSQL, S3, SQS에 가까운 환경에서 통합 테스트

Java 21은 현재 LTS 버전이며, record와 pattern matching으로 DTO와 메시지 모델을 간결하게 작성할 수 있다.

### Spring Data JPA, QueryDSL, Flyway

- JPA로 도메인 모델과 연관관계를 표현하고, 단순 CRUD 코드를 줄인다.
- 아카이브 조회처럼 조건이 많은 조회는 QueryDSL로 타입 안전하게 작성하고, fetch join과 projection으로 N+1을 제어한다.
- 스키마는 JPA 자동 생성(`ddl-auto`)에 맡기지 않고 Flyway 마이그레이션으로 관리한다.

### PostgreSQL, pgvector

기존 팀 프로젝트는 MySQL을 사용했지만, 리빌드에서는 PostgreSQL을 사용한다.

- pgvector 확장으로 별도 벡터 DB 없이 의미 검색 구현 가능
- 검색 결과를 가족 ID 같은 일반 조건과 한 쿼리에서 함께 필터링 가능
- `SELECT ... FOR UPDATE SKIP LOCKED`로 Outbox relay를 여러 인스턴스에서 안전하게 실행 가능

### FastAPI

기존 AI 서버는 Flask 기반으로 빠르게 모델 기능을 API로 노출하는 데 적합한 구조였다. 이는 캡스톤 일정과 AI 기능 검증 목적에는 합리적인 선택이었다.

다만 리빌드에서는 AI 서버를 제품형 API/worker로 운영해야 하므로 FastAPI로 재구성한다.

- 요청/응답 스키마를 타입 기반으로 명확히 정의 가능
- OpenAPI/Swagger 문서 자동 생성
- Pydantic 기반 validation으로 메시지 계약과 LLM 구조화 출력 검증이 쉬움
- 같은 코드베이스에서 동기 API(얼굴 정렬, 임베딩)와 SQS worker를 함께 운영 가능

이 전환은 Flask가 잘못된 선택이었다는 의미가 아니다. 기존 Flask 서버는 빠른 AI 기능 검증에 적합했고, 리빌드에서는 API 계약과 운영 구조를 더 명확히 하기 위해 FastAPI를 선택한다.

### AWS S3

영상과 썸네일 파일은 DB에 저장하지 않고 S3에 저장한다. DB에는 object key와 메타데이터만 저장한다.

- 대용량 영상 파일 저장에 적합
- 앱, API 서버, AI worker가 동일한 파일 저장소를 공유 가능
- presigned URL로 앱이 API 서버를 거치지 않고 안전하게 직접 업로드 가능

### AWS SQS

AI 분석은 오래 걸리므로 API 요청과 분리한다.

- 앱 요청을 빠르게 종료 가능
- AI 서버 장애가 전체 서비스 장애로 번지는 것을 줄임
- visibility timeout과 DLQ로 실패한 작업 재시도와 격리 가능
- AI worker를 여러 대로 확장 가능

### AWS CloudFront

S3에 저장된 영상과 썸네일을 클라이언트에 전달하는 CDN이다. 초기에는 S3 presigned GET URL로 시작할 수 있지만, 목표 아키텍처에서는 CloudFront를 포함해 미디어 전송 경로를 설계한다.

## 4. 프론트엔드 아키텍처

### 모노레포 구성

```txt
apps/
  mobile/              Expo 앱
  web/                 웹 클라이언트
packages/
  design-tokens/       색상, 타이포그래피, 간격 등 플랫폼 중립 토큰
  api-client/          OpenAPI 스펙에서 생성한 타입과 클라이언트
  core/                검증 스키마, 도메인 상수, 포맷터, 쿼리 키
```

UI 컴포넌트는 플랫폼별로 각 앱 안에 두되, 같은 토큰을 사용하고 가능한 한 같은 컴포넌트 API(이름, props)를 유지한다.

### 디자인 시스템

- **토큰 계층**: 원시 값(palette, scale)과 의미 토큰(`color.text.primary`, `space.md`)을 나누고, 화면과 컴포넌트는 의미 토큰만 사용한다.
- **공통 컴포넌트**: 화면은 공통 컴포넌트의 조합으로 만든다. 화면 코드에 색상, 간격 같은 스타일 값을 직접 쓰지 않는다.
- **접근성 기준**: 고령 사용자를 고려해 기본 글자 크기, 대비, 최소 터치 영역을 토큰과 컴포넌트 기본값에 반영하고, 시스템 글자 크기 확대에 레이아웃이 깨지지 않도록 한다.
- **상태 표현**: 빈 상태, 로딩, 에러, 분석 중 상태를 공통 컴포넌트로 통일한다.
- **문서화**: Storybook으로 컴포넌트와 상태별 변형을 확인할 수 있게 한다.

### API 연동

- Spring API의 OpenAPI 스펙에서 `packages/api-client`를 생성한다. 서버 DTO가 바뀌면 클라이언트 빌드에서 타입 오류로 드러난다.
- 공통 HTTP 계층에서 인증 헤더, 에러 응답 변환, 타임아웃을 처리한다.
- 서버의 공통 에러 코드를 클라이언트 에러 메시지와 UI 처리 규칙에 매핑한다.

### 인증

```txt
요청 -> 401 (access token 만료)
-> refresh 요청 (진행 중인 refresh가 있으면 그 결과를 기다림)
-> 성공: 새 토큰 저장 후 원래 요청 재시도
-> 실패: 토큰 삭제, 로그인 화면으로 이동
```

- 모바일은 토큰을 SecureStore에 저장한다.
- 여러 요청이 동시에 401을 받아도 refresh는 한 번만 요청한다. 서버의 refresh rotation과 충돌하지 않게 하기 위해서다.
- 웹은 XSS와 CSRF 위험을 고려해 토큰 저장 방식을 별도로 결정한다(httpOnly 쿠키 등).

### 서버 상태와 분석 상태 추적

- 서버 데이터는 TanStack Query로 관리하고, 쿼리 키는 `packages/core`에 모아 모바일과 웹이 같이 쓴다.
- 분석 중인 답변만 polling하고, `READY` 또는 `FAILED`가 되면 polling을 멈춘다.
- 댓글/반응은 낙관적 업데이트로 즉시 반영하고, 실패하면 롤백한다.

### 영상 업로드

- presigned URL로 S3에 직접 업로드하고 진행률을 표시한다.
- 업로드 실패 시 재시도할 수 있고, 완료 등록 API는 서버에서 멱등하게 처리되므로 재요청해도 안전하다.
- 업로드 중 앱을 벗어나는 경우의 처리 범위는 모바일 앱 구현 단계에서 결정한다.

### 테스트

| 대상          | 도구                                    |
| ------------- | --------------------------------------- |
| 공통 로직     | Jest                                    |
| 컴포넌트      | React Native Testing Library, Storybook |
| 모바일 E2E    | Maestro                                 |
| 웹 E2E        | Playwright                              |

## 5. 백엔드 아키텍처

### 패키지 구조

```txt
com.woorizip
  auth/        로그인, 토큰 발급/재발급
  family/      가족, 멤버십, 초대 코드
  question/    주차별 질문
  answer/      영상 답변, 업로드
  comment/     댓글, 반응
  analysis/    분석 job, 결과 반영
  search/      의미 검색
  common/      공통 에러, 인가, 응답 형식
  infra/       S3, SQS, Outbox relay
```

각 도메인 패키지는 `api`(controller, DTO), `application`(service), `domain`(entity, repository) 계층으로 나눈다.

### 인증과 인가

- access token은 짧게, refresh token은 DB에 저장하고 재발급 시 rotation한다.
- 가족 단위 리소스는 모두 요청한 사용자의 멤버십을 확인한 뒤 접근한다. 조회 쿼리에는 항상 `familyId` 조건을 포함한다.
- 초대 코드 가입은 `(family_id, user_id)` 유니크 제약으로 중복 가입을 막고, 가족 인원 제한은 락으로 동시 가입을 제어한다.

### Transactional Outbox

**해결하려는 문제**

업로드 완료 등록 시 "답변 상태를 `UPLOADED`로 저장"하는 것과 "SQS에 분석 job 발행"은 서로 다른 시스템에 대한 쓰기다. 둘을 따로 실행하면 한쪽만 성공하는 경우가 생긴다.

- DB 커밋 후 SQS 발행 전에 서버가 종료됨 → 답변은 있는데 분석이 영원히 시작되지 않음
- SQS 발행 후 DB 커밋이 롤백됨 → 존재하지 않는 답변에 대한 분석이 실행됨

`@TransactionalEventListener(AFTER_COMMIT)`으로 커밋 후 발행하는 방식은 두 번째 문제는 막지만, 커밋 직후 프로세스가 종료되면 이벤트가 메모리에서 사라지므로 첫 번째 문제는 여전히 남는다.

**설계**

```txt
[업로드 완료 트랜잭션]
UPDATE video_answer SET status = 'UPLOADED'
INSERT INTO outbox_event (aggregate_id, event_type, payload, status = 'PENDING')
COMMIT

[Outbox Relay, 주기 실행]
SELECT ... FROM outbox_event WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED
-> SQS analysis-jobs 발행
-> status = 'PUBLISHED'
```

- 이벤트가 DB에 함께 커밋되므로 유실되지 않는다.
- relay는 발행 후 상태 갱신 전에 종료될 수 있으므로 **at-least-once**로 동작한다. 따라서 소비자 측 멱등 처리가 필수다.

### 결과 큐 분리

**해결하려는 문제**

AI worker가 분석 결과를 DB에 직접 쓰면, 스키마를 Spring(JPA/Flyway)과 Python 두 곳이 동시에 알아야 한다. 마이그레이션 한 번에 두 서비스가 같이 영향을 받고, 상태 전이 규칙도 두 곳에 흩어진다.

**설계**

- AI worker는 결과를 `analysis-results` 큐에 메시지로 발행한다.
- Spring API가 이를 소비해 상태 전이 규칙에 따라 DB에 반영한다.
- 서비스 간 계약은 DB 스키마가 아니라 메시지 스키마다. 메시지에는 `schemaVersion`을 포함한다.

### 멱등성

| 지점                 | 중복 원인                   | 처리                                                  |
| -------------------- | --------------------------- | ----------------------------------------------------- |
| 업로드 완료 등록     | 앱 재시도, 네트워크 재전송  | `UPLOADING` 상태에서만 `UPLOADED`로 전이, 이후 요청은 현재 상태 반환 |
| 분석 job 수신 (AI)   | Outbox at-least-once, SQS 재전달 | `jobId` 기준으로 이미 완료된 job은 건너뜀           |
| 분석 결과 수신 (API) | SQS 재전달                  | `jobId`와 `attempt` 기준으로 이미 반영된 결과는 무시  |

## 6. AI 아키텍처

### 구성

AI 서버는 하나의 Python 코드베이스에서 두 가지 역할을 한다.

- **Worker**: SQS `analysis-jobs`를 소비해 영상 분석 파이프라인 실행
- **내부 API**: Spring API만 호출하는 동기 엔드포인트
  - 얼굴 정렬 가이드: 촬영 전 프레임의 얼굴 위치/크기 판정
  - 임베딩: 검색 질의를 벡터로 변환

### 분석 파이프라인

```txt
TRANSCRIBE -> SUMMARIZE -> THUMBNAIL -> EMBED
```

| 단계       | 입력           | 출력                | 구현                                            |
| ---------- | -------------- | ------------------- | ----------------------------------------------- |
| TRANSCRIBE | 영상 오디오    | 전사 텍스트         | faster-whisper                                  |
| SUMMARIZE  | 전사 텍스트    | 제목, 요약          | LLM (제공자 추상화), 구조화 출력                |
| THUMBNAIL  | 영상 프레임    | 썸네일 이미지       | MediaPipe 얼굴 검출, OpenCV 선명도 점수         |
| EMBED      | 전사, 요약     | 벡터                | 임베딩 모델                                     |

- 각 단계의 결과, 처리 시간, 사용한 모델/프롬프트 버전을 결과 메시지에 포함한다.
- 필수 단계(TRANSCRIBE, SUMMARIZE, THUMBNAIL)가 실패하면 job은 `FAILED`다.
- 선택 단계(EMBED)가 실패하면 답변은 `READY`로 두고 검색 대상에서만 제외한 뒤, 이후 재처리한다.

### LLM 제공자 추상화

LLM 제공자는 아직 확정하지 않는다. 기존 팀 프로젝트의 Gemini를 유지할지, 다른 제공자로 바꿀지는 평가 결과를 보고 결정한다.

- 파이프라인 코드는 `LLMClient` 인터페이스에만 의존하고, 제공자별 구현을 설정으로 교체한다.
- 프롬프트는 코드와 함께 버전 관리하고, 결과에 프롬프트 버전을 기록한다.
- 출력은 Pydantic 스키마로 검증하고, 검증 실패 시 1회 재시도 후 fallback(제목 = 질문 텍스트)을 사용한다.

### 썸네일 선택

영상에서 후보 프레임을 샘플링한 뒤 다음 점수를 합산해 가장 높은 프레임을 선택한다.

- 얼굴 검출 여부와 얼굴 크기/위치
- 선명도(Laplacian variance)
- 눈 뜸 여부, 표정

점수 가중치와 선택 근거는 평가셋 결과와 함께 문서화한다.

### 의미 검색

```txt
Expo App -> Spring API : 검색어
Spring API -> AI 내부 API : 검색어 임베딩
Spring API -> PostgreSQL : pgvector 유사도 검색 (WHERE family_id = ?)
Spring API -> Expo App : 검색 결과
```

벡터 저장과 검색은 모두 Spring API가 소유한 PostgreSQL에서 수행하므로, 가족 단위 접근 제어가 일반 조회와 동일하게 적용된다.

### 평가

| 대상   | 지표                                   |
| ------ | -------------------------------------- |
| STT    | CER(한국어 기준), 처리 시간            |
| 요약   | 기준표 기반 평가(사실성, 간결성), 실패율 |
| 썸네일 | 사람이 고른 프레임과의 일치율          |
| 공통   | job당 처리 시간, 비용                  |

평가셋은 동의받은 샘플 영상으로 구성하고, 모델이나 프롬프트를 바꿀 때마다 같은 평가셋으로 비교한다.

## 7. 핵심 처리 흐름

### 일반 데이터 흐름

```txt
Expo App -> Spring API -> PostgreSQL
```

가족 생성, 가족 초대, 주차별 질문, 답변 목록, 댓글, 아카이브 목록은 Spring API가 요청을 받고 PostgreSQL에서 데이터를 읽거나 쓴다.

### 영상 업로드 흐름

```txt
Expo App -> Spring API : 업로드 URL 요청
Spring API : VideoAnswer(UPLOADING) 생성, S3 presigned URL 발급
Expo App -> S3 : 영상 직접 업로드
Expo App -> Spring API : 업로드 완료 등록
Spring API : VideoAnswer(UPLOADED) + Outbox 이벤트 저장
```

영상 파일은 DB에 저장하지 않는다. DB에는 S3 object key, 질문 ID, 작성자 ID, 상태, 썸네일 key, 제목, 요약 같은 메타데이터만 저장한다.

### AI 분석 흐름

```txt
Outbox Relay -> SQS analysis-jobs
AI Worker <- SQS analysis-jobs
AI Worker : S3 영상 다운로드 -> 분석 파이프라인 실행
AI Worker -> SQS analysis-results
Spring API <- SQS analysis-results : 결과 반영
```

앱은 분석 완료를 기다리지 않고, 분석 상태를 polling해서 `ANALYZING`, `READY`, `FAILED` 상태를 보여준다.

### 실패와 재시도

- worker 처리 중 예외가 나면 메시지를 삭제하지 않는다. visibility timeout 이후 다시 수신된다.
- 최대 수신 횟수(예: 3회)를 넘으면 DLQ로 이동하고, 답변은 `FAILED`가 된다.
- `FAILED` 답변은 재분석 API로 새 Outbox 이벤트를 만들어 다시 요청할 수 있다.
- 분석이 실패해도 영상 자체는 재생 가능하며, 앱은 기본 썸네일과 질문 텍스트로 fallback 표시한다.

### 미디어 조회 흐름

```txt
S3 -> CloudFront -> Expo App
```

## 8. 상태 설계

### VideoAnswer

```txt
UPLOADING -> UPLOADED -> ANALYZING -> READY
                                   -> FAILED -> ANALYZING (재분석)
```

- `UPLOADING`: presigned URL 발급 완료, 앱에서 S3로 업로드 중
- `UPLOADED`: S3 업로드 완료 등록, 분석 이벤트 저장 완료
- `ANALYZING`: 분석 job 발행 후 결과 대기 중
- `READY`: 필수 분석 단계 완료
- `FAILED`: 필수 분석 단계 실패, 재분석 가능

### AnalysisJob

- 상태: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`
- 단계별 상태와 결과: TRANSCRIBE, SUMMARIZE, THUMBNAIL, EMBED
- 시도 횟수, 모델 버전, 프롬프트 버전, 단계별 처리 시간

## 9. 로컬 개발 환경

```txt
docker compose up
  postgres    PostgreSQL + pgvector
  localstack  S3, SQS
```

API 서버와 AI 서버는 로컬에서 직접 실행하고, 위 인프라에 연결한다. 통합 테스트는 Testcontainers로 같은 구성을 테스트마다 띄운다.

환경은 `local`, `dev`, `prod`로 나누고, 비밀값은 레포에 두지 않는다. 레포에는 `.env.example`만 둔다.

## 10. 도메인 전략

기존 `woorizip.site` 도메인은 팀 프로젝트 배포 과정에서 사용된 흔적이 있으므로, 개인 리빌드의 공식 도메인으로 재사용할지는 소유권과 팀 프로젝트와의 경계를 확인한 뒤 결정한다.

초기에는 Expo preview URL, API 배포 플랫폼 기본 URL, CloudFront 기본 도메인으로 개발한다. 서비스 배포가 안정화된 이후 개인 소유 도메인 또는 별도 서브도메인을 연결한다.
