# woorizip-rebuild

가족 Q&A 기반 영상 아카이빙 서비스 **우리.zip**의 리빌드 프로젝트입니다.

기존 팀 캡스톤 프로젝트는 로컬 환경에서 시연 가능한 수준까지 구현되었지만, 배포 환경에서는 서비스 간 연결이 안정적으로 동작하지 않았습니다. 이 프로젝트는 서비스 아이디어와 핵심 기능은 이어받고, 클라이언트, 백엔드, AI 전 영역을 **실제 운영 가능한 구조**로 다시 설계합니다.

## 리빌드 방향

- **클라이언트**: 디자인 시스템을 새로 정의하고, 모바일 앱을 먼저 만든 뒤 웹 클라이언트를 별도로 구축합니다. 두 클라이언트는 디자인 토큰, API 클라이언트, 공통 로직을 공유합니다.
- **백엔드**: Spring Boot 위에서 도메인 중심 구조, 가족 단위 인가, 비동기 처리의 정합성을 다룹니다.
- **AI**: 영상 분석을 단계별 비동기 파이프라인으로 재구성하고, 평가 기준을 세워 품질을 측정합니다. 분석 결과를 활용한 의미 검색도 제공합니다.
- **인프라**: 영상은 S3에 직접 업로드하고, 분석은 SQS로 분리하며, 미디어는 CloudFront로 전달합니다.

## 주요 설계 과제

**프론트엔드**

- 디자인 토큰과 공통 컴포넌트 기반 화면 구성, 접근성 기준 반영
- 모바일/웹 간 토큰, OpenAPI 기반 API 클라이언트, 공통 로직 공유
- 토큰 자동 갱신과 동시 요청 중 갱신 요청 단일화
- 서버 상태 캐싱, 낙관적 업데이트, 분석 상태 polling
- 영상 업로드 진행률 표시와 실패 복구

**백엔드**

- Transactional Outbox로 답변 저장과 분석 요청 발행의 정합성 보장
- 분석 요청과 푸시 알림을 같은 Outbox 이벤트 흐름으로 처리
- AI 결과를 결과 큐로 받아 DB 쓰기 주체를 API 서버로 일원화
- 업로드 완료 등록과 메시지 중복 수신에 대한 멱등성 처리
- 이메일 로그인과 카카오/애플 소셜 로그인을 하나의 계정 모델로 통합
- 가족 멤버십 기반 인가, 초대 코드 가입 동시성 처리
- Testcontainers, LocalStack 통합 테스트와 k6 부하 테스트

**AI**

- 전사 → 요약 → 썸네일 → 임베딩 단계별 비동기 파이프라인
- STT 모델 비교, 평가셋 기반 품질 측정
- LLM 제공자 추상화와 프롬프트 버전 관리
- pgvector 기반 가족 아카이브 의미 검색

## 기술 스택

| 영역          | 스택                                                    |
| ------------- | ------------------------------------------------------- |
| Mobile App    | Expo, React Native, TypeScript                          |
| Web App       | React, TypeScript                                       |
| Client Shared | Design Tokens, OpenAPI Client, TanStack Query, Zod      |
| API Server    | Spring Boot 3, Java 21, Spring Security, JWT            |
| Auth          | 이메일, 카카오, 애플 로그인                             |
| Persistence   | Spring Data JPA, QueryDSL, Flyway                       |
| Database      | PostgreSQL, pgvector                                    |
| AI Server     | FastAPI, Python, faster-whisper, MediaPipe              |
| Storage/CDN   | AWS S3, CloudFront                                      |
| Queue         | AWS SQS                                                 |
| Push          | Expo Push (FCM, APNs)                                   |
| Test          | Jest, RNTL, Maestro, Playwright, JUnit5, Testcontainers, pytest, k6 |
| CI            | GitHub Actions                                          |

## 디렉터리 구조

```txt
apps/
  mobile/          Expo 앱 (expo-router)
  api/             Spring Boot API 서버 (예정)
  ai/              FastAPI AI 서버, 분석 worker (예정)
  web/             웹 클라이언트 (예정)

packages/
  design-tokens/   플랫폼 중립 디자인 토큰
  api-client/      OpenAPI 기반 API 클라이언트
  core/            검증 스키마, 도메인 상수, 공통 로직

infra/
  docker-compose.yml       PostgreSQL(pgvector), LocalStack(S3, SQS)
  localstack/init-aws.sh   로컬 S3 버킷, SQS 큐 생성

docs/
  rebuild-plan.md
  architecture.md
```

## 로컬 실행

```bash
# 의존성 설치 (npm workspace)
npm install

# 로컬 인프라: PostgreSQL(5432), LocalStack(4566)
cd infra && docker compose up -d

# 모바일 앱
npm run mobile:start
```

## 문서

- [리빌드 계획](./docs/rebuild-plan.md)
- [아키텍처](./docs/architecture.md)
- [사용자 흐름과 화면 목록](./docs/user-flows.md)

## 현재 상태

1단계(기반 설계 및 모노레포 정리)를 마치고 2단계(디자인 시스템과 핵심 화면 디자인)를 준비하고 있습니다.

- 완료: 리빌드 계획·아키텍처·사용자 흐름 문서, npm workspace 모노레포, Expo 앱 초기화와 라우팅 설정, 공유 패키지 구조, 로컬 인프라 구성

## 원본 프로젝트

우리.zip은 팀 캡스톤 프로젝트로 시작되었습니다.  
이 레포지토리는 원본 팀 프로젝트와 독립적으로 진행하는 리빌드이며, 원본 코드를 그대로 가져오지 않고 서비스 아이디어와 핵심 기능을 바탕으로 새로 설계하고 구현합니다.
