package com.woorizip.auth.api;

import com.woorizip.auth.application.LoginService;
import com.woorizip.auth.application.SessionTokens;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인, 토큰 재발급, 로그아웃. 로그인 없이 쓰는 API다(SecurityConfig에서 연다). */
@RestController
@RequestMapping("/auth")
@SecurityRequirements
class AuthController {

	private final LoginService login;

	private final SessionTokens sessions;

	AuthController(LoginService login, SessionTokens sessions) {
		this.login = login;
		this.sessions = sessions;
	}

	/** 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 응답(401 LOGIN_FAILED)을 준다. */
	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return TokenResponse.from(this.login.login(request.email(), request.password()));
	}

	/** refresh token을 새것으로 바꾸고 access token을 새로 발급한다. 이전 refresh token은 더 쓸 수 없다. */
	@PostMapping("/token/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return TokenResponse.from(this.sessions.refresh(request.refreshToken()));
	}

	/** 그 기기의 refresh token을 폐기한다. access token 없이 할 수 있다. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@Valid @RequestBody RefreshRequest request) {
		this.sessions.logout(request.refreshToken());
	}

	record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotNull String password) {
	}

	record RefreshRequest(@NotBlank String refreshToken) {
	}

}
