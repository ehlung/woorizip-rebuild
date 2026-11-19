# 0018. 메일 발송

- 상태: 채택
- 날짜: 2026-11-20
- 관련 문제: 01의 4장 ([01-legacy-analysis.md](../01-legacy-analysis.md))

## 맥락

기존 BE는 개인 Gmail 계정의 SMTP로 메일을 보냈다(`BE src/main/resources/application-prod.properties:15`, `spring.mail.host=smtp.gmail.com`). 그 메일 계정의 비밀번호가 git 이력에 남았다(01의 4장 비밀값 표).

0단계에는 메일이 세 군데 필요하다.

- 가입 이메일 인증 코드([03-architecture.md](../03-architecture.md) 8장)
- 비밀번호 재설정 코드([ADR 0009](0009-auth-token.md))
- 이미 가입된 이메일로 코드를 요청했을 때의 안내

보내는 양은 적다. 0단계는 가족 단위의 테스트 배포이고, 메일은 가입과 비밀번호 재설정 때만 나간다.

## 판단 기준

1. 오래 사는 비밀값(계정 비밀번호, API 키)을 새로 만들지 않는다([ADR 0014](0014-secrets-and-config.md))
2. 서비스 도메인으로 보내고, 발신 도메인을 인증해 스팸함에 빠지지 않게 할 수 있다
3. 이메일 주소를 맡기는 곳이 늘지 않고, 처리 지역이 한국이면 좋다(개인정보 처리 수탁자, 국외 이전 공개)
4. 비용과 운영이 1인 개발로 감당할 만하다
5. 로컬과 테스트에서 실제 메일 없이 흐름 전체를 확인할 수 있다([ADR 0016](0016-local-and-test.md))

## 후보

2026-11-20 각 공식 페이지 기준이다.

### A. 개인 메일 계정의 SMTP (기존)

- 장점: 바로 쓸 수 있다.
- 단점:
  - 계정 비밀번호나 앱 비밀번호라는 오래 사는 비밀값이 생긴다(기준 1). Google은 앱 비밀번호를 권장하지 않는다("App passwords aren't recommended and are unnecessary in most cases.", https://support.google.com/accounts/answer/185833).
  - 발신 주소가 개인 Gmail 주소라 서비스 도메인으로 보낼 수 없다(기준 2).
  - 하루 500건을 넘기면 발송이 막힐 수 있다(https://support.google.com/mail/answer/22839).

### B. Amazon SES

- 장점:
  - API 서버가 IAM 역할로 SES API를 호출하므로 비밀값이 없다(기준 1). SES의 SMTP 방식은 SMTP 자격 증명이 따로 필요하므로 쓰지 않는다.
  - 도메인을 인증하고 Easy DKIM을 켜면 SES가 보내는 메일마다 DKIM 서명을 붙인다("Amazon SES automatically adds a 2048-bit DKIM key to every email that you send from that identity.", https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dkim-easy.html)(기준 2).
  - 서울 리전(ap-northeast-2)에서 쓸 수 있다(https://docs.aws.amazon.com/general/latest/gr/ses.html). S3와 Parameter Store로 이미 AWS를 쓰고 있어 이메일 주소를 맡기는 곳이 늘지 않는다(기준 3).
- 단점:
  - 새 계정은 sandbox 상태로 시작한다. 인증한 주소에만 보낼 수 있고, 24시간에 200건, 초당 1건으로 제한된다. 운영 권한을 신청해야 하고, 첫 응답은 96시간 안에 온다(https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html).
  - 도메인 인증에는 DNS 설정이 필요하다. 도메인은 아직 정하지 않았다(ADR 0017).
  - 반송과 수신 거부를 관리하지 않으면 계정 평판이 떨어질 수 있다.
- 비용: 2025-06-01 이후 사용 기록이 없는 새 계정은 2026-07-21부터 Essentials 플랜으로 시작하고, 월 기본료 없이 1,000건당 $0.16이다(https://aws.amazon.com/ses/pricing/). 0단계 규모에서는 월 몇 센트 수준이다.

### C. 메일 전문 서비스 (Resend, SendGrid 등)

- 장점: 설정이 쉽고, 발송 기록과 템플릿 도구가 갖춰져 있다.
- 단점:
  - API 키라는 오래 사는 비밀값이 생긴다(기준 1).
  - 이메일 주소를 맡기는 업체가 하나 늘어, 처리방침에 수탁자로 공개해야 한다. 국외 업체면 국외 이전 항목도 공개해야 한다(기준 3). 각 업체의 처리 지역은 확인하지 않았다.
- 비용: Resend는 무료 플랜이 월 3,000건, 하루 100건이다(https://resend.com/pricing). SendGrid는 영구 무료 플랜 없이 60일 체험(하루 100건)만 있다(https://www.twilio.com/en-us/products/email-api/pricing).

## 결정

**B(Amazon SES, 서울 리전)를 채택하고, API 서버가 IAM 역할로 SES API를 호출한다.**

- A는 기존 비밀값 유출과 같은 종류의 위험을 다시 만들고, 서비스 도메인으로 보낼 수 없다.
- C는 비용과 편의에서 나쁘지 않지만, 비밀값과 수탁자가 하나씩 늘어난다. B는 둘 다 늘리지 않는다.

### 발송 방식

- 메일은 요청을 처리하는 트랜잭션에서 발송 작업으로 저장하고, 커밋 직후 바로 보낸다. 실패하면 API 서버의 스케줄러가 간격을 두고 다시 보낸다. 푸시 알림과 같은 방식이다([03-architecture.md](../03-architecture.md) 7장).
- 메일 본문에는 코드와 안내만 담고, 답변 내용이나 가족 정보는 넣지 않는다.
- 로컬과 테스트에서는 가짜 발송 구현을 쓴다. 보낸 메일을 기록해 두고, 테스트는 그 기록에서 코드를 읽어 가입과 재설정 흐름을 끝까지 확인한다(0016).

### 준비 순서

1. 개발 계정은 sandbox 그대로 두고, 개발자 본인 주소만 인증해 실제 발송을 확인한다.
2. 도메인이 정해지면(0017) 도메인을 인증하고 Easy DKIM을 켠다. 발신 주소는 그 도메인의 발신 전용 주소로 한다.
3. 테스트 배포 일정보다 충분히 앞서 운영 권한을 신청한다. 첫 응답까지 최대 96시간이 걸린다.

## 결과

- 얻는 것: 메일 발송에 비밀값이 없다. 이메일 주소를 맡기는 곳이 AWS 하나로 유지된다. 서울 리전에서 처리한다.
- 잃는 것: 운영 권한 신청과 도메인 인증이라는 준비 절차가 생긴다. 반송과 수신 거부 관리를 직접 해야 한다.
- 후속 작업:
  - 0017에서 도메인을 정한 뒤 도메인 인증과 DKIM 설정
  - 반송과 수신 거부 알림을 받아 해당 주소로 다시 보내지 않는 처리. 운영 권한 신청 전에 정한다

## 검증

assertion:

- 저장소와 설정에 메일 계정 비밀번호, SMTP 자격 증명, 메일 서비스 API 키가 없다.
- 로컬과 테스트에서 가짜 발송으로 가입 인증과 비밀번호 재설정 흐름을 끝까지 통과한다.
- 메일 발송이 일시적으로 실패해도 재시도해 결국 보낸다.

테스트 배포 전 확인:

- SES 계정이 서울 리전에서 운영 권한을 받았다.
- 발신 도메인의 DKIM 인증이 완료되어 있다.
