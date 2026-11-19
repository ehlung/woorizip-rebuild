# 0012. AI 제공자 선택 (STT, 요약)

- 상태: 제안 (조건에 따른 후보 선별까지 완료. 최종 선택은 품질 평가 후 확정)
- 날짜: 2026-11-20
- 관련 문제: P-19, P-20 ([01-legacy-analysis.md](../01-legacy-analysis.md))

## 맥락

기존 AI 서버는 오디오를 Gemini 2.5 Flash에 바로 넣어 요약과 제목만 받았다. STT 단계가 없어 transcript가 남지 않았고(P-19), 가족 음성을 외부 AI로 보내면서 동의를 받지 않았으며, 제공자의 약관도 검토하지 않았다(P-20).

리빌드는 분석을 STT(음성 → 글)와 요약(글 → 제목·요약)으로 나눈다. 두 단계의 제공자를 각각 고른다.

서비스 조건은 다음과 같다([02-domain-and-problem.md](../02-domain-and-problem.md) 2장).

- 가족 서비스라 미성년자가 함께 쓴다. 0단계는 14세 이상만 가입하고, 14세 미만 보호자 동의 가입은 3단계다.
- 개인정보를 국외로 이전하면 개인정보 처리방침에 공개하거나 별도 동의를 받아야 한다(개인정보 보호법 제28조의8).
- 입력은 연세가 많은 화자를 포함한 한국어 일상 대화다.

## 판단 기준

1. 약관상 미성년자가 쓰는 서비스에서 사용할 수 있다
2. 입력한 가족 음성과 글을 제공자가 학습이나 홍보에 쓰지 않는다. 처리 후 제공자 쪽에 남는 입력과 결과는 우리가 지울 수 있거나, 정해진 기간 뒤 지워진다([03-architecture.md](../03-architecture.md) 9장)
3. 처리 지역이 한국이면 더 좋다. 국외 이전 공개와 동의 부담이 줄어든다
4. 한국어, 특히 연세가 많은 화자의 일상 대화를 잘 처리한다
5. 비용이 운영 규모에 비해 감당할 만하다

기준 1과 2는 통과해야 하는 조건이고, 3~5는 비교 기준이다.

## 조건으로 제외한 후보

| 후보 | 제외 이유 | 근거 |
| --- | --- | --- |
| Gemini API | 18세 미만이 쓸 가능성이 있는 서비스에서 사용 금지 (기준 1) | Gemini API 추가 약관(2026-03-23 시행): "will not use the Services as part of a website, application, or other service ... that is directed towards or is likely to be accessed by individuals under the age of 18" (https://ai.google.dev/gemini-api/terms) |
| Vertex AI의 Gemini | Google Cloud에서도 같은 연령 제한 (기준 1) | Google Cloud Service Specific Terms §20(d) Age Restrictions(2026-11-18 수정): "Customer will not, and will not allow End Users to, use a Generative AI Service as part of a website, Customer Application, or other online service that is directed towards or is likely to be accessed by individuals under the age of 18." (https://cloud.google.com/terms/service-terms) |
| 네이버 CLOVA Studio (HyperCLOVA X) | 입력 데이터에 개인정보를 넣지 못하게 하고, 입력과 결과를 품질 개선과 홍보에 쓸 수 있다 (기준 2) | CLOVA Studio 서비스 이용약관(2025-07-17 적용) 제10조②: 고객 데이터에 포함되지 않도록 해야 할 항목으로 "개인정보"를 명시. 제10조①: "회사의 홍보 목적, 서비스의 품질 및 기능 개선을 위한 목적으로 사용할 수 있습니다" (https://www.ncloud.com/policy/terms/stdio) |
| Google Cloud Speech-to-Text | 한국어 모델을 서울 리전에서 쓸 수 있다는 근거가 없고(기준 3), Chirp 모델에 생성형 AI 연령 조항이 적용되는지 불분명하다(기준 1). 요금도 분당 $0.016으로 다른 후보보다 높다 | 지원 언어 문서의 ko-KR 리전 목록에 asia-northeast3 없음 (https://cloud.google.com/speech-to-text/v2/docs/speech-to-text-supported-languages) |

기존 프로젝트가 쓰던 Gemini는 연령 조항 때문에 이 서비스에 쓸 수 없다. 약관을 검토하지 않았던 문제(P-20)가 실제로 사용 불가로 이어진 사례다.

## 남은 후보

2026-11-20 각 공식 문서 기준이다.

### STT

| 후보 | 한국어 | 처리 지역 | 학습 사용 | 미성년자 | 비용 |
| --- | --- | --- | --- | --- | --- |
| AWS Transcribe | ko-KR 지원 | 서울(ap-northeast-2) | 기본으로 서비스 개선에 사용. AWS Organizations의 AI 서비스 opt-out 정책으로 끌 수 있다 | 제한 조항 없음 | 분당 $0.006, 월 60분 12개월 무료 |
| 네이버 CLOVA Speech | 장문 인식 지원 | 한국 리전 | 고객이 동의한 경우에만 사용. 인식 결과와 로그는 7일 보관 | 최종 사용자 제한 조항 없음 | 15초당 5원(분당 약 20원), 월 20분 무료 |
| 직접 실행 (faster-whisper) | 지원 | 우리 서버 | 해당 없음 | 해당 없음 | 서버 비용 |

근거:

- AWS Transcribe: 지원 언어 https://docs.aws.amazon.com/transcribe/latest/dg/supported-languages.html , 서울 엔드포인트 https://docs.aws.amazon.com/general/latest/gr/transcribe.html , 요금 https://aws.amazon.com/transcribe/pricing/ , 서비스 개선 사용 AWS Service Terms 50.3 https://aws.amazon.com/service-terms/ , opt-out https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_ai-opt-out.html
- CLOVA Speech: 한국 리전 https://guide.ncloud-docs.com/docs/clovaspeech-overview , 학습 사용 CLOVA Speech 서비스 이용약관 제4조②("고객의 동의가 있는 경우 엔진 성능향상을 위한 목적 범위 내에서"), 보관 제7조① https://www.ncloud.com/policy/terms/clsph , 요금 https://www.ncloud.com/product/aiService/clovaSpeech
- faster-whisper: MIT 라이선스, CPU 벤치마크는 small 모델만 공개(i7-12700K 8스레드, 13분 오디오 int8 1분 42초) https://github.com/SYSTRAN/faster-whisper . 일반적인 4 vCPU 서버에서 3분 오디오에 약 1~5분이 걸릴 것으로 추정하며, 실측이 필요하다. 무음 구간에서 없는 말을 만들어 내는 문제가 Whisper 논문과 모델 카드에 기록되어 있어 음성 구간 검출(VAD)이 필요하다.

OpenAI gpt-transcribe는 분당 $0.0045이고 받아쓰기 입력을 보관하지 않지만, 공식 문서에서 한국어 지원 근거를 찾지 못했고 처리 지역이 미국과 EU뿐이다. 한국어 지원이 확인되면 평가 대상에 다시 넣는다.

### 요약

| 후보 | 처리 지역 | 학습 사용 | 미성년자 | 비용 (100만 토큰당 입력/출력) |
| --- | --- | --- | --- | --- |
| AWS Bedrock의 Claude | Sonnet 5, Opus 5는 서울 안에서 처리(In-Region). Haiku 계열은 전 세계 라우팅만 가능 | Anthropic은 고객 콘텐츠로 학습하지 않음. Bedrock은 데이터를 저장하지 않는 설정이 있음 | Anthropic 미성년자 가이드라인 적용 | 서울 처리 모델 가격 미확인 |
| Anthropic API (Claude Haiku 5.5) | 미국 또는 전 세계 | 학습하지 않음, 30일 이내 삭제 | Anthropic 미성년자 가이드라인 적용 | $0.10 / $0.50 |
| OpenAI 소형 모델 | 한국에서는 저장만 되고 처리는 되지 않음 | 학습하지 않음, 남용 감시용 30일 보관 | 미성년자는 보호자 동의 필요 | 모델에 따라 $0.05~0.25 / $0.40~2.00 |

근거:

- Bedrock 리전별 모델: https://docs.aws.amazon.com/bedrock/latest/userguide/models-region-compatibility.html , 데이터 보관: https://docs.aws.amazon.com/bedrock/latest/userguide/data-retention.html , Bedrock의 Anthropic 약관: https://aws.amazon.com/legal/bedrock/third-party-models/
- Anthropic 학습 제한: Commercial Terms "Anthropic may not train models on Customer Content from Services." https://www.anthropic.com/legal/commercial-terms , 미성년자 가이드라인: https://support.claude.com/en/articles/9307344-responsible-use-of-anthropic-s-models-guidelines-for-organizations-serving-minors , 가격: https://platform.claude.com/docs/en/about-claude/pricing , 처리 지역: https://platform.claude.com/docs/en/manage-claude/data-residency
- OpenAI 데이터 사용: https://developers.openai.com/api/docs/guides/your-data , 처리 지역: https://developers.openai.com/api/docs/guides/data-residency , 미성년자: Services Agreement §3.3 "allow minors to use OpenAI Services without consent from their parent or guardian" 금지 https://cdn.openai.com/osa/openai-services-agreement.pdf , 13세 미만은 데이터 미보관 적용 후에만 처리 https://developers.openai.com/api/docs/guides/safety-checks/under-18-api-guidance

Anthropic 미성년자 가이드라인은 미성년자가 쓰는 서비스를 금지하지 않는 대신, AI와 상호작용한다는 사실의 고지, 연령 확인 같은 안전장치, 관련 법령 준수를 요구하고 Anthropic이 주기적으로 점검한다고 밝힌다.

### 비용

3분 답변 하나에 STT는 약 2~6센트, 요약은 1센트 미만이다. 지인 테스트 규모에서는 월 몇 달러 수준이라 비용은 후보 사이의 차이를 만들지 않는다. 선택은 처리 지역과 한국어 품질로 한다.

## 결정 (제안)

조건(기준 1, 2)으로 후보를 다음과 같이 좁힌다. 최종 선택은 품질 평가 후 확정한다.

- **STT 평가 대상**: AWS Transcribe, CLOVA Speech, faster-whisper
- **요약 평가 대상**: AWS Bedrock의 Claude, Anthropic API
- 제외: Gemini API, Vertex AI의 Gemini, CLOVA Studio, Google Cloud Speech-to-Text
- 보류: OpenAI(gpt-transcribe의 한국어 지원 근거 확인 후 재검토)

### 검토 중인 방향

STT를 AWS Transcribe(서울)로, 요약을 Bedrock의 서울 처리 모델로 하면 영상과 글이 모두 한국 안에서 처리된다. 영상은 이미 S3에 있고, [ADR 0005](0005-service-communication.md)에서 우선 후보로 둔 클라우드 권한(IAM 역할) 방식의 인증을 함께 쓸 수 있다. 다만 이는 조건만 본 것이며, 한국어 품질은 평가로 확인한다. CLOVA Speech가 한국어 품질에서 앞설 가능성도 있다.

### 품질 평가 계획

- **평가셋**: 동의를 받은 가족 답변 영상 샘플. 연세가 많은 화자, 사투리, 배경 소음, 말이 거의 없는 영상을 포함한다.
- **STT**: 사람이 직접 받아 적은 정답과 비교한 문자 오류율(CER), 처리 시간, 무음 구간에서 없는 말을 만들어 내는지
- **요약**: rubric으로 채점한다. 사실성(말하지 않은 내용을 지어내지 않는가), 간결성, 가족이 읽기 편한 말투. rubric과 기준값은 [04-evaluation.md](../04-evaluation.md)와 ADR 0013에서 정한다.
- 같은 평가셋을 모델이나 프롬프트를 바꿀 때마다 다시 사용한다.

## 결과

- 얻는 것: 약관상 쓸 수 없는 제공자를 개발 전에 걸러 냈다. 남은 후보는 모두 미성년자 사용과 학습 미사용 조건을 만족한다.
- 잃는 것: 기존 프로젝트의 Gemini 기반 요약을 이어 쓸 수 없다. 평가셋을 새로 모아야 한다.
- 후속 작업:
  - 확인할 것: Bedrock 서울 처리 모델의 가격, OpenAI gpt-transcribe의 한국어 지원, 약관의 "미성년자"가 한국 민법 기준(만 19세 미만)으로 해석되는지, CLOVA Speech 학습 동의의 설정 위치, 제공자별로 처리 후 남는 데이터를 지우는 방법(AWS Transcribe 작업 결과 삭제, CLOVA Speech의 7일 보관을 앞당겨 지울 수 있는지, Anthropic API의 보관 기간을 줄이는 설정이 있는지)
  - AWS Transcribe를 쓰면 AWS Organizations의 AI 서비스 opt-out 정책을 먼저 설정한다
  - Anthropic 모델을 쓰면 미성년자 가이드라인의 안전장치(AI 사용 고지 등)를 0단계 동의 화면과 약관에 반영한다
  - 평가 후 이 ADR을 "채택"으로 바꾸고, [ADR 0017](README.md)(배포 형태)과 [ADR 0005](0005-service-communication.md)를 확정한다

## 검증

- 평가셋 결과로 STT와 요약 각각의 선택 근거(오류율, rubric 점수, 처리 시간)를 이 ADR에 기록한다.
- 선택한 제공자의 데이터 사용 설정(학습 opt-out, 데이터 미보관 설정)이 운영 계정에 적용되어 있다.
- 개인정보 처리방침에 선택한 제공자를 처리 수탁자로, 국외에서 처리되는 경우 국외 이전 항목과 함께 공개한다.
