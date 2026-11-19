# 0007. 클라이언트 플랫폼

- 상태: 채택
- 날짜: 2026-11-20
- 관련 문제: P-10, P-25 ([01-legacy-analysis.md](../01-legacy-analysis.md))

## 맥락

기존 클라이언트는 React 웹이었다. 녹화 형식을 webm으로 고정해 webm 녹화를 지원하지 않는 iPhone Safari에서 실패할 수 있었고(P-25), 카메라는 HTTPS나 localhost에서만 열려 휴대폰으로 개발 중인 화면을 테스트하기도 어려웠다. 알림 기능은 없었다. 업로드 중 새로고침하면 녹화본이 사라졌다(P-10).

0단계에는 다음이 필요하다.

- 720p, H.264 mp4, 최대 3분 촬영 ([ADR 0003](0003-video-upload.md))
- 새 질문, 가족 답변, 분석 완료·실패 푸시 알림 ([02-domain-and-problem.md](../02-domain-and-problem.md) 6장, [ADR 0002](0002-async-analysis.md), [ADR 0004](0004-failure-and-retry.md))
- 업로드 진행률, 실패 시 녹화본 보존과 다시 올리기 ([ADR 0004](0004-failure-and-retry.md))

리빌드는 1인 개발이고, 개발자는 React 경험이 있다. 2단계에 열람용 웹을 만든다.

## 판단 기준

1. iOS와 Android 모두에서 푸시 알림을 받을 수 있다
2. 녹화 형식과 길이를 제어할 수 있다
3. 업로드 진행률을 보여주고, 앱이 백그라운드로 가도 업로드를 이어 갈 수 있다
4. 1인 개발로 iOS와 Android를 함께 만들고 테스트 배포할 수 있다
5. 2단계의 웹과 코드(디자인 토큰, API 클라이언트, 공통 로직)를 공유할 수 있다

## 후보

### A. 웹 (PWA 포함)

- 장점: 설치가 필요 없다. 초대 링크를 누르면 바로 들어올 수 있다.
- 단점: iOS에서 웹 푸시는 홈 화면에 추가한 웹 앱에서만 쓸 수 있다("Now with iOS and iPadOS 16.4, we are adding support for Web Push to Home Screen web apps.", https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/). 연세가 많은 사용자에게 홈 화면 추가를 기대하기 어렵다(기준 1). 브라우저마다 녹화 형식이 달라 기존 문제(P-25)가 남는다(기준 2).

### B. 플랫폼별 네이티브 (Swift, Kotlin)

- 장점: 카메라, 업로드, 알림을 가장 세밀하게 제어한다.
- 단점: 두 플랫폼을 따로 만들어야 해 1인 개발로 감당하기 어렵다(기준 4). 웹과 코드를 공유할 수 없다(기준 5).

### C. Flutter

- 장점: 한 코드로 두 플랫폼을 만든다.
- 단점: Dart를 새로 익혀야 한다. 웹 클라이언트(React)와 디자인 토큰, API 클라이언트를 TypeScript로 공유할 수 없다(기준 5).

### D. React Native (Expo)

- 장점: React와 TypeScript로 두 플랫폼을 만든다(기준 4). 웹과 TypeScript 패키지를 공유한다(기준 5). 기능별 근거:
  - 녹화: `expo-camera`의 `videoQuality`('720p' 등), `maxDuration`, `videoBitrate` 옵션과 iOS 코덱 지정(`codec`) (https://docs.expo.dev/versions/latest/sdk/camera/)
  - 업로드: `expo-file-system`의 업로드 작업과 진행률 콜백. iOS에서는 백그라운드 세션이 기본값이다("Determines whether the iOS native session should continue in the background.", https://docs.expo.dev/versions/latest/sdk/filesystem/)
  - 푸시: Expo 푸시 서비스는 발송 비용이 없다("There is no cost associated with sending notifications through Expo push notification service.", https://docs.expo.dev/push-notifications/faq/)
  - 빌드: EAS Build 무료 플랜은 월 Android 15회, iOS 15회이고, 한도를 넘으면 로컬 빌드로 계속할 수 있다 (https://expo.dev/pricing , https://docs.expo.dev/billing/faq/)
- 단점: 카카오처럼 네이티브 SDK가 필요한 기능은 Expo Go로 실행할 수 없어 개발용 빌드(dev build)가 필요하다. 아래 "확인할 것"처럼 일부 촬영 옵션은 플랫폼마다 지원이 다르다.

## 결정

**D(Expo)를 채택한다.** 앱은 TypeScript로 작성하고, 2단계의 웹은 별도 앱으로 만들되 `packages/`의 디자인 토큰, API 클라이언트, 공통 로직을 공유한다([ADR 0006](0006-repository-and-api-contract.md)).

- A는 iOS 푸시 제약 때문에 매주 질문을 알리는 핵심 흐름을 보장할 수 없다.
- B와 C는 1인 개발 조건과 웹과의 코드 공유에서 D보다 불리하다.
- 웹을 버리면서 잃는 것은 "설치 없이 초대 링크로 바로 들어오기"다. 초대 링크는 앱이 있으면 앱을, 없으면 스토어를 열도록 하고, 설치 후 초대 코드를 자동으로 채우는 기능은 1단계에 둔다.

### 확인할 것 (구현 초기에 실기기로 검증)

공식 문서만으로는 다음을 확정하지 못했다. 0003의 영상 형식을 지키기 위해 구현 초기에 실기기로 확인한다.

- Android에서 녹화 코덱을 지정하는 옵션이 문서에 없다. Android 기기가 H.264로 녹화하는지 확인한다.
- `videoQuality`의 `720p`가 iOS에서도 적용되는지 문서 표현이 모호하다.
- 녹화 파일의 컨테이너(mp4 또는 mov)가 문서에 없다.

기기에서 형식이 지켜지지 않으면, 업로드 완료 확인 단계에서 형식을 검사하고 서버의 변환 단계에서 재생용 영상을 H.264 mp4로 다시 만든다. 이 처리 위치는 [03-architecture.md](../03-architecture.md)에서 정한다.

## 결과

- 얻는 것: 두 플랫폼에서 푸시, 녹화 제어, 백그라운드 업로드를 쓸 수 있다. 웹과 TypeScript 코드를 공유한다.
- 잃는 것: 설치라는 단계가 생긴다. 일부 촬영 옵션이 플랫폼마다 달라 실기기 확인이 필요하다.
- 후속 작업: 구현 초기에 iOS와 Android 실기기에서 녹화 형식, 크기, 백그라운드 업로드를 확인하고 이 ADR에 결과를 적는다.

## 검증

assertion:

- iOS와 Android 실기기에서 3분 녹화 파일이 720p, H.264, 100MB 미만이다(04-evaluation의 A-12와 같은 확인).
- 업로드 중 앱을 백그라운드로 보내도 iOS에서 업로드가 이어진다.
- 앱이 꺼져 있을 때 보낸 푸시 알림을 누르면 해당 화면이 열린다.
