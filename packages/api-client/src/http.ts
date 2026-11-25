// 생성된 요청 함수(src/generated)가 모두 이 함수로 요청을 보낸다.
// 서버 주소는 코드에 쓰지 않고 앱이 시작할 때 설정한다 (ADR 0006, ADR 0014).
// 인증 헤더와 401 재발급(03-architecture 8장)은 로그인 기능을 만들 때 여기에 더한다.

let baseUrl: string | undefined;

export function configureApiClient(options: { baseUrl: string }): void {
  baseUrl = options.baseUrl.replace(/\/+$/, '');
}

export async function apiFetch<T>(url: string, options: RequestInit): Promise<T> {
  if (baseUrl === undefined) {
    throw new Error('API 서버 주소가 없습니다. 앱이 시작할 때 configureApiClient를 먼저 호출하세요.');
  }
  const response = await fetch(`${baseUrl}${url}`, options);
  const text = await response.text();
  // 오류 응답도 본문(ApiProblem)을 그대로 돌려준다. 앱은 status와 data.code로 처리 방식을 정한다.
  const data: unknown = text.length > 0 ? JSON.parse(text) : undefined;
  return { data, status: response.status, headers: response.headers } as T;
}
