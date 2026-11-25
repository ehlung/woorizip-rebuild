import { defineConfig } from 'orval';

// 서버가 만든 OpenAPI 명세(openapi.json)로 앱용 요청 함수와 타입을 만든다 (ADR 0006).
// openapi.json은 apps/api의 테스트(OpenApiSpecTest)가 쓴다. 직접 고치지 않는다.
export default defineConfig({
  woorizip: {
    input: {
      target: './openapi.json',
    },
    output: {
      target: './src/generated/api.ts',
      client: 'fetch',
      // 이전에 생성한 파일 중 지금 명세에 없는 것을 지운다.
      clean: true,
      override: {
        // 모든 요청이 이 함수를 거친다. 서버 주소, 인증 헤더, 401 처리를 한곳에 둔다.
        mutator: {
          path: './src/http.ts',
          name: 'apiFetch',
        },
      },
    },
  },
});
