package com.woorizip.auth.api;

import java.time.Instant;
import java.util.Set;

import com.woorizip.auth.application.SignupService;
import com.woorizip.user.domain.ConsentKind;
import com.woorizip.user.domain.ScreenSize;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 이메일 가입. 로그인 없이 쓰는 API다(SecurityConfig에서 연다). */
@RestController
@RequestMapping("/auth/signup")
@SecurityRequirements
class SignupController {

	private final SignupService signup;

	SignupController(SignupService signup) {
		this.signup = signup;
	}

	/** 가입 인증 코드를 메일로 보낸다. 이미 가입된 이메일이어도 응답은 같다. */
	@PostMapping("/email-code")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void requestCode(@Valid @RequestBody EmailCodeRequest request) {
		this.signup.requestCode(request.email());
	}

	/** 코드를 확인하고 가입 확인 값을 발급한다. */
	@PostMapping("/email-code/verify")
	VerifyEmailCodeResponse verifyCode(@Valid @RequestBody VerifyEmailCodeRequest request) {
		SignupService.IssuedTicket ticket = this.signup.verifyCode(request.email(), request.code());
		return new VerifyEmailCodeResponse(ticket.signupToken(), ticket.expiresAt());
	}

	/** 계정을 만들고 토큰을 발급한다. 가입이 끝나면 바로 로그인 상태가 된다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TokenResponse signup(@Valid @RequestBody SignupRequest request) {
		return TokenResponse.from(this.signup.signup(new SignupService.SignupCommand(request.signupToken(),
				request.password(), request.name(), request.screenSize(), request.consents())));
	}

	record EmailCodeRequest(@NotBlank @Email @Size(max = 320) String email) {
	}

	record VerifyEmailCodeRequest(@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Pattern(regexp = "\\d{6}") String code) {
	}

	record VerifyEmailCodeResponse(String signupToken, Instant expiresAt) {
	}

	record SignupRequest(@NotBlank String signupToken, @NotNull String password, @NotBlank @Size(max = 20) String name,
			@NotNull ScreenSize screenSize, @NotNull Set<ConsentKind> consents) {
	}

}
