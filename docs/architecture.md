# 우리.zip 리빌드 아키텍처

## 1. 목표 아키텍처

```txt
Expo App
-> API Server
-> PostgreSQL

Expo App
-> API Server
-> S3 Presigned URL 발급
-> App이 S3에 영상 직접 업로드
-> API Server에 업로드 완료 등록

API Server
-> SQS에 AI 분석 job 등록

AI Worker / FastAPI
-> SQS job 수신
-> S3 영상 다운로드
-> 썸네일 생성
-> STT 요약
-> 분석 결과 저장

App
-> 분석 상태 polling
-> 결과 표시

S3
-> CloudFront
-> 앱에서 영상/썸네일 조회
```

이 구조의 핵심은 **영상 업로드, 메타데이터 저장, AI 분석, 미디어 조회를 분리하는 것**이다. 영상과 AI 처리는 오래 걸리고 실패 가능성이 있으므로, 앱 요청 안에서 모든 작업을 동기적으로 처리하지 않는다.

## 2. 기술 스택

| 영역         | 신규 스택                      |
| ------------ | ------------------------------ |
| Mobile App   | Expo, React Native, TypeScript |
| API Server   | Node.js, Express, TypeScript   |
| Database     | PostgreSQL                     |
| ORM          | Prisma                         |
| AI Server    | FastAPI, Python                |
| File Storage | AWS S3                         |
| CDN          | AWS CloudFront                 |
| Queue        | AWS SQS                        |
| Monitoring   | CloudWatch, Sentry             |
| App Build    | Expo EAS                       |
| Repository   | 개인 모노레포                  |

## 3. 스택 선택 이유

### Expo React Native

우리.zip은 모바일 중심 서비스이므로 웹앱보다 네이티브 앱이 서비스 성격에 더 적합하다.

- React Native 기반 모바일 앱을 빠르게 개발 가능
- 카메라, 파일, 미디어, 알림 등 앱 기능을 쉽게 사용할 수 있음
- EAS Build를 통해 Android/iOS 빌드 경험을 만들 수 있음
- 앱 개발 경험이 없는 상태에서 순수 React Native보다 진입 장벽이 낮음

### Node.js + Express + TypeScript

백엔드는 기존 Spring Boot를 유지할 수도 있지만, 이번 리빌드의 핵심 목표는 Java 학습이 아니라 **모바일 앱과 AI, 클라우드 배포까지 포함한 완성도 있는 서비스 구축**이다.

따라서 익숙한 Node.js 기반으로 빠르게 구조를 잡되, TypeScript, Prisma, 계층 분리, API 문서화, 테스트를 통해 실무형 백엔드로 구성한다.

### Prisma

Prisma를 ORM으로 사용한다.

- TypeScript 백엔드와 타입 안정성이 좋음
- schema, migration, seed data를 한곳에서 관리하기 쉬움
- PostgreSQL과 잘 맞음
- 포트폴리오에서 DB 모델링과 migration 흐름을 설명하기 좋음

### FastAPI

기존 AI 서버는 Flask 기반으로 빠르게 모델 기능을 API로 노출하는 데 적합한 구조였다. 이는 캡스톤 일정과 AI 기능 검증 목적에는 합리적인 선택이었다.

다만 리빌드에서는 AI 서버를 제품형 API/worker로 운영해야 하므로 FastAPI로 재구성한다.

- 요청/응답 스키마를 타입 기반으로 명확히 정의 가능
- OpenAPI/Swagger 문서 자동 생성
- Pydantic 기반 validation으로 API 계약 관리가 쉬움
- SQS worker, S3 다운로드, AI 분석 job 구조와 결합하기 좋음

이 전환은 Flask가 잘못된 선택이었다는 의미가 아니다. 기존 Flask 서버는 빠른 AI 기능 검증에 적합했고, 리빌드에서는 API 계약과 운영 구조를 더 명확히 하기 위해 FastAPI를 선택한다.

## 4. 핵심 처리 흐름

### 일반 데이터 흐름

```txt
Expo App -> API Server -> PostgreSQL
```

가족 생성, 가족 초대, 주차별 질문, 답변 목록, 댓글, 아카이브 목록은 API 서버가 요청을 받고 PostgreSQL에서 데이터를 읽거나 쓴다.

### 영상 업로드 흐름

```txt
Expo App
-> API Server에 업로드 URL 요청
-> API Server가 S3 presigned URL 발급
-> Expo App이 S3에 직접 업로드
-> Expo App이 API Server에 업로드 완료 등록
-> API Server가 VideoAnswer 메타데이터 저장
```

영상 파일은 DB에 저장하지 않는다. DB에는 S3 object key, 질문 ID, 작성자 ID, 분석 상태, 썸네일 key, 제목, 요약 같은 메타데이터만 저장한다.

### AI 분석 흐름

```txt
API Server
-> SQS에 분석 job 등록

AI Worker
-> SQS에서 job 수신
-> S3에서 영상 다운로드
-> 썸네일 생성
-> STT 요약
-> 분석 결과 저장
```

AI 분석은 오래 걸릴 수 있으므로 앱/API 요청과 분리한다. 앱은 분석 완료를 기다리지 않고, 분석 상태를 polling해서 `ANALYZING`, `READY`, `FAILED` 상태를 보여준다.

### 미디어 조회 흐름

```txt
S3 -> CloudFront -> Expo App
```

S3는 원본 영상과 썸네일을 저장하고, CloudFront는 앱이 미디어를 더 안정적으로 조회할 수 있도록 앞단 CDN 역할을 한다.

## 5. AWS 서비스 역할

### S3

영상과 썸네일 파일을 저장한다. 앱, API 서버, AI worker가 동일한 저장소를 기준으로 파일을 주고받는다.

### SQS

AI 분석 작업을 대기열로 분리한다. API 서버는 작업을 큐에 넣고, AI worker는 큐에서 작업을 가져가 처리한다.

### CloudFront

S3에 저장된 영상과 썸네일을 앱에 전달하는 CDN 역할을 한다. 초기에는 S3 URL만으로 시작할 수 있지만, 목표 아키텍처에서는 CloudFront를 포함한다.

## 6. 상태 설계

영상 답변은 다음 상태를 가진다.

```txt
UPLOADING
-> UPLOADED
-> ANALYZING
-> READY
-> FAILED
```

- `UPLOADING`: 앱에서 S3로 업로드 중
- `UPLOADED`: S3 업로드 완료, API 서버에 답변 메타데이터 등록 완료
- `ANALYZING`: AI 분석 작업 진행 중
- `READY`: 썸네일, 제목, 요약 생성 완료
- `FAILED`: AI 분석 실패 또는 재시도 필요

## 7. 도메인 전략

기존 `woorizip.site` 도메인은 팀 프로젝트 배포 과정에서 사용된 흔적이 있으므로, 개인 리빌드의 공식 도메인으로 재사용할지는 소유권과 팀 프로젝트와의 경계를 확인한 뒤 결정한다.

초기에는 Expo preview URL, API 배포 플랫폼 기본 URL, CloudFront 기본 도메인으로 개발한다. 포트폴리오 배포가 안정화된 이후 개인 소유 도메인 또는 별도 서브도메인을 연결한다.
