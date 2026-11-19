# 결정 기록 (ADR)

결정 하나를 파일 하나로 기록한다. 형식은 [0000-template.md](0000-template.md)를 따른다.

- 채택된 결정은 고치지 않는다. 결정이 바뀌면 새 ADR을 쓰고, 이전 ADR의 상태를 "대체됨"으로 바꾼다.
- 관련 문제의 번호(P-xx)는 [01-legacy-analysis.md](../01-legacy-analysis.md)의 문제 번호다.
- 상태는 `작성 전`, `제안`, `채택`, `대체됨`으로 표시한다.

## 용어

- **리빌드 초기 설계안**: ADR을 쓰기 전에 작성한 설계 문서다. 커밋 [`4412bfd`](https://github.com/ehlung/woorizip-rebuild/tree/4412bfd35da846135a3f0fabaa8abd959e2bcc32/docs)의 [`docs/architecture.md`](https://github.com/ehlung/woorizip-rebuild/blob/4412bfd35da846135a3f0fabaa8abd959e2bcc32/docs/architecture.md)와 [`docs/rebuild-plan.md`](https://github.com/ehlung/woorizip-rebuild/blob/4412bfd35da846135a3f0fabaa8abd959e2bcc32/docs/rebuild-plan.md)를 말한다. 이 문서들은 ADR과 03-architecture로 내용을 옮긴 뒤 저장소에서 지우며, 링크는 해당 커밋을 가리키므로 지운 뒤에도 열린다.

## 목록

| 번호 | 결정 | 관련 문제 | 상태 |
| --- | --- | --- | --- |
| [0001](0001-mvp-scope.md) | MVP 범위와 기능 우선순위 | 01의 5장, P-05, P-10, P-16 | 채택 |
| [0002](0002-async-analysis.md) | 업로드 후 AI 분석을 요청과 분리하는 방식 | P-05, P-06, P-08, P-10 | 채택 |
| [0003](0003-video-upload.md) | 영상 형식과 업로드 경로 | P-05, P-25 | 채택 |
| [0004](0004-failure-and-retry.md) | 분석 실패 상태와 재시도 | P-07, P-08, P-09, P-10 | 채택 |
| [0005](0005-service-communication.md) | 서비스 간 통신과 AI 서버 보호 | P-21, 01의 4장 | 제안 |
| [0006](0006-repository-and-api-contract.md) | 저장소 구성과 API 명세 공유 | P-01, P-02, P-03 | 채택 |
| [0007](0007-client-platform.md) | 클라이언트 플랫폼 | P-10, P-25 | 채택 |
| [0008](0008-family-authorization.md) | 가족 단위 권한 확인 | P-13 | 채택 |
| [0009](0009-auth-token.md) | 인증 토큰의 발급, 저장, 갱신 | P-12, P-14, P-15 | 채택 |
| [0010](0010-database-and-schema.md) | DB 선택과 스키마 관리 | P-04, P-17 | 채택 |
| [0011](0011-ai-worker-framework.md) | AI 워커 구성 | P-01, P-03, P-06 | 채택 |
| [0012](0012-ai-provider.md) | AI 제공자 선택 (STT, 요약) | P-19, P-20 | 제안 |
| 0013 | AI 결과 품질 평가 | P-18 | 작성 전 |
| [0014](0014-secrets-and-config.md) | 비밀값과 환경별 설정 관리 | P-02, P-12, 01의 4장 | 채택 |
| [0015](0015-screen-size-and-accessibility.md) | 화면 크기 3단계와 접근성 | P-22, P-23, P-24 | 채택 |
| [0016](0016-local-and-test.md) | 로컬 실행과 테스트 전략 | P-03, P-04, 01의 4장 | 채택 |
| 0017 | 배포 형태 | 01의 2장(배포 형태), P-03 | 작성 전 |
| [0018](0018-email-delivery.md) | 메일 발송 | 01의 4장 | 채택 |

작성 순서는 0단계 구현을 막는 결정부터다. 0002~0004로 핵심 흐름을, 0006~0011과 0014~0016으로 0단계 구현의 기반을 정했다. 0005는 원칙만 제안한 상태다. 0005를 확정하려면 0012(AI 제공자)와 0017(배포 형태)을 먼저 정해야 한다. 나머지는 구현하면서 필요한 시점에 쓴다. 번호와 제목은 작성하면서 바뀔 수 있다.
