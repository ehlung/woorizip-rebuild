# woorizip-rebuild

가족 Q&A 기반 영상 아카이빙 서비스 **우리.zip**의 개인 리빌드 프로젝트입니다.

기존 팀 캡스톤 프로젝트의 서비스 아이디어와 핵심 기능을 기반으로, 웹앱 중심 구조를 **Expo 기반 모바일 앱**으로 전환하고, 영상 업로드/AI 분석/클라우드 저장 구조를 제품형 아키텍처로 재설계합니다.

## 목표

- 모바일 앱 중심 UX 재설계
- 영상 업로드와 AI 분석의 비동기 처리 구조 구현
- S3, CloudFront, SQS 기반 미디어 아키텍처 설계
- Express API, FastAPI AI worker, PostgreSQL 기반 백엔드 재구성
- 배포 가능한 end-to-end 서비스 완성

## 기술 스택

| 영역        | 스택                           |
| ----------- | ------------------------------ |
| Mobile App  | Expo, React Native, TypeScript |
| API Server  | Node.js, Express, TypeScript   |
| Database    | PostgreSQL                     |
| ORM         | Prisma                         |
| AI Server   | FastAPI, Python                |
| Storage/CDN | AWS S3, CloudFront             |
| Queue       | AWS SQS                        |

## 예정 구조

```txt
apps/
  mobile/
  api/
  ai/

packages/
  shared/

docs/
  rebuild-plan.md
  architecture.md
```

## 문서

- [리빌드 계획](./docs/rebuild-plan.md)
- [아키텍처](./docs/architecture.md)

## 현재 상태

초기 설계 및 레포지토리 세팅 단계입니다.

## 원본 프로젝트 맥락

기존 우리.zip은 팀 캡스톤 프로젝트로 진행되었습니다.  
이 레포지토리는 원본 팀 프로젝트를 직접 덮어쓰지 않고, 서비스 아이디어와 핵심 기능을 기반으로 개인 포트폴리오용으로 재설계하는 독립 리빌드입니다.
