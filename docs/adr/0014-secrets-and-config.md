# 0014. 비밀값과 환경별 설정 관리

- 상태: 채택
- 날짜: 2026-11-20
- 관련 문제: P-02, P-12, 01의 4장 ([01-legacy-analysis.md](../01-legacy-analysis.md))

## 맥락

기존 프로젝트에서는 비밀값이 반복해서 공개 저장소에 들어갔다.

- AI 저장소에 AWS 액세스 키가 커밋되어 있었다. 커밋할 때 GitHub의 push protection이 막았지만 우회하고 올렸다(01의 4장).
- BE 소스 코드에 JWT 서명 키가 하드코딩되어 있었다 (P-12)
- 설정 파일에 넣었다가 추적을 해제하는 커밋이 여러 번 있었지만, 이전 커밋에는 값이 그대로 남았다(01의 4장).
- 운영 CI는 GCP 서비스계정 키를 Docker 이미지 안에 넣어 빌드했다.
- 서비스 주소가 코드 여러 곳에 하드코딩되어, 서버를 옮길 때마다 여러 파일을 고쳐야 했다 (P-02)
- 로컬과 운영 설정이 달라 운영에서만 생기는 버그가 있었다(01의 4장).

`.gitignore`로 설정 파일을 빼는 방식은 실수 한 번에 무너졌다. 비밀값을 사람이 조심해서 다루는 것만으로는 막지 못했다는 것이 기존 프로젝트의 교훈이다.

## 판단 기준

1. 비밀값이 저장소, 빌드 결과물(이미지, 앱 번들)에 들어가지 않는다
2. 실수로 커밋하려 해도 도구가 막고, 한 장치를 우회해도 다른 장치가 막는다
3. 오래 사는 비밀값의 수를 줄인다
4. 환경(로컬, 운영)별 차이를 한곳에서 관리하고, 필요한 설정이 빠지면 서버가 시작하지 않는다
5. 1인 운영으로 관리할 수 있고, 비용이 작다

## 후보

### 비밀값 보관

#### A. 저장소 밖의 설정 파일 (기존)

- 장점: 단순하다.
- 단점: `.gitignore`를 놓치거나 파일을 잘못 추가하면 바로 노출된다(기준 1). 기존 프로젝트에서 실제로 반복됐다.

#### B. CI 비밀값으로 환경 변수 주입

GitHub Actions의 비밀값에 넣고, 배포할 때 환경 변수로 주입한다.

- 장점: 저장소에 값이 없다.
- 단점: 비밀값이 배포 설정마다 흩어진다. 값을 바꾸면 재배포가 필요하다. 이미지에 넣는 실수(기존 GCP 키)를 막지 못한다.

#### C. 클라우드 비밀값 저장소

AWS Systems Manager Parameter Store(SecureString) 또는 Secrets Manager에 두고, 서비스는 IAM 역할로 읽는다.

- 장점: 저장소, CI, 이미지 어디에도 값이 없다(기준 1). 서비스가 IAM 역할로 읽으므로 읽기용 비밀값이 따로 필요 없다(기준 3). 값을 한곳에서 바꾼다.
- 단점: AWS에 묶인다. 로컬 개발용 설정을 따로 준비해야 한다.
- 비용: Parameter Store의 Standard 파라미터는 추가 요금이 없다("Standard parameters are available at no additional charge.", https://aws.amazon.com/systems-manager/pricing/). SecureString은 기본 `aws/ssm` 키로 암호화되며, KMS 요청은 월 20,000건까지 무료다(https://aws.amazon.com/kms/pricing/). Secrets Manager는 비밀값 하나에 월 $0.40이다(https://aws.amazon.com/secrets-manager/pricing/).

### 커밋 방지 장치

| 장치 | 범위 | 한계 |
| --- | --- | --- |
| GitHub push protection (저장소 단위) | 공개 저장소는 무료. 푸시할 때 알려진 형식의 비밀값을 막는다 | 기본으로 쓰기 권한이 있으면 사유를 적고 우회할 수 있다. 우회 권한을 제한하는 기능(delegated bypass)은 유료 플랜 기능이다 (https://docs.github.com/en/get-started/learning-about-github/about-github-advanced-security) |
| GitHub secret scanning | 공개 저장소는 자동, 무료. 이미 올라간 비밀값을 찾아 알린다 | 정해진 형식의 키만 찾는다. 임의의 문자열 비밀값은 못 찾는다 |
| gitleaks (MIT) | 커밋 전 훅과 CI에서 검사한다 (https://github.com/gitleaks/gitleaks) | 로컬 훅은 `SKIP=gitleaks`로 건너뛸 수 있다. CI 검사는 건너뛸 수 없다 |

## 결정

**비밀값은 C(Parameter Store SecureString)에 두고, 커밋 방지 장치를 세 겹으로 둔다.** 구체적인 배포 형태는 ADR 0017에서 정하지만, 앞선 결정들([ADR 0003](0003-video-upload.md), [ADR 0012](0012-ai-provider.md))이 AWS를 기준으로 하고 있어 AWS의 저장소를 쓴다.

### 비밀값

- 운영 비밀값은 Parameter Store의 SecureString(Standard)에 둔다. Secrets Manager의 자동 교체 기능은 지금 필요하지 않아 비용이 없는 Parameter Store를 쓴다.
- 서비스는 IAM 역할로 읽는다. Spring은 Spring Cloud AWS의 `spring.config.import=aws-parameterstore:...`로 읽고, 필요한 값이 없으면 시작하지 않는다.
- 비밀값은 저장소, CI 로그, 컨테이너 이미지, 앱 번들에 넣지 않는다. 앱에 들어가는 값은 공개되어도 되는 값(예: API 주소)만 둔다.
- 앞선 결정으로 오래 사는 비밀값 자체를 줄였다. 서비스 간 인증과 AWS 서비스 호출은 IAM 역할로 한다([ADR 0005](0005-service-communication.md)). 남는 비밀값은 JWT 서명 키([ADR 0009](0009-auth-token.md))와 외부 서비스 키(메일 발송 등) 정도다.

### 커밋 방지

1. 이 저장소에는 secret scanning과 push protection이 켜져 있다(2026-11-20 확인). 개인 저장소라 우회 권한을 제한할 수 없으므로, **push protection은 우회하지 않는다**를 규칙으로 둔다. 막히면 커밋에서 값을 빼고 다시 커밋한다.
2. gitleaks를 커밋 전 훅으로 설치한다.
3. CI에서 gitleaks를 실행해, 비밀값이 발견되면 병합할 수 없게 한다. 앞의 두 장치를 우회해도 여기서 막힌다.
4. 그래도 비밀값이 올라가면, 저장소에서 지우는 것보다 **값을 먼저 폐기하고 새로 발급**한다. 이력에 남은 값은 지워지지 않기 때문이다. JWT 서명 키는 0009의 키 교체 절차를 따른다. 새 키를 발급하고 이전 키를 폐기하면, 앱은 refresh token으로 재발급받아 사용자가 다시 로그인하지 않는다.

### 환경별 설정

- 설정 이름과 기본값은 코드의 설정 파일에 두고, 환경마다 다른 값(주소, 버킷 이름 등)은 환경 변수나 Parameter Store에서 덮어쓴다. Spring은 환경 변수가 설정 파일 값을 덮어쓴다(https://docs.spring.io/spring-boot/reference/features/external-config.html).
- 서비스 주소는 코드에 쓰지 않고 설정에서만 읽는다(P-02).
- 로컬 개발은 Docker Compose와 LocalStack으로 같은 구조를 흉내 내고, 로컬 전용 값은 커밋하지 않는 `.env.local`에 둔다. 저장소에는 값 없이 이름만 적은 `.env.example`을 둔다.
- 로컬과 운영의 차이는 "값"뿐이고 "설정 항목"은 같게 유지한다. 운영에만 있는 설정 항목이 생기지 않도록, 필수 설정이 빠지면 로컬에서도 시작하지 않게 한다.

## 결과

- 얻는 것: 비밀값이 저장소와 빌드 결과물에 들어가지 않는다. 한 장치를 우회해도 CI에서 막힌다. 오래 사는 비밀값 수가 줄었다.
- 잃는 것: AWS에 묶인다. 로컬 개발 환경을 따로 준비해야 한다.
- 후속 작업: CI 구성 시 gitleaks 단계 추가, 0017 확정 후 IAM 역할별 읽기 권한 정리

## 검증

assertion:

- CI에서 gitleaks 검사가 실패하면 병합할 수 없다.
- 저장소의 전체 이력에 대해 gitleaks 검사가 통과한다.
- 컨테이너 이미지와 앱 번들 안에 비밀값이 없다.
- 필수 설정이 빠지면 서버가 시작하지 않는다.
- 코드 안에 서비스 주소가 하드코딩되어 있지 않다(0006과 같은 확인).
