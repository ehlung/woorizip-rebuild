package com.woorizip.auth.api;

import java.time.Instant;

import com.woorizip.auth.application.PasswordResetService;
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

/** 비밀번호 재설정. 로그인 없이 쓰는 API다(SecurityConfig에서 연다). */
@RestController
@RequestMapping("/auth/password-reset")
@SecurityRequirements
class PasswordResetController {

	private final PasswordResetService reset;

	PasswordResetController(PasswordResetService reset) {
		this.reset = reset;
	}

	/** 재설정 코드를 메일로 보낸다. 가입하지 않은 이메일이어도 응답은 같다. */
	@PostMapping("/email-code")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void requestCode(@Valid @RequestBody EmailCodeRequest request) {
		this.reset.requestCode(request.email());
	}

	/** 코드를 확인하고 재설정 확인 값을 발급한다. */
	@PostMapping("/email-code/verify")
	VerifyResetCodeResponse verifyCode(@Valid @RequestBody VerifyResetCodeRequest request) {
		PasswordResetService.IssuedTicket ticket = this.reset.verifyCode(request.email(), request.code());
		return new VerifyResetCodeResponse(ticket.resetToken(), ticket.expiresAt());
	}

	/** 비밀번호를 바꾼다. 다른 기기의 로그인도 모두 끊기고, 앱은 로그인 화면으로 간다. */
	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reset(@Valid @RequestBody ResetPasswordRequest request) {
		this.reset.reset(request.resetToken(), request.newPassword());
	}

	record EmailCodeRequest(@NotBlank @Email @Size(max = 320) String email) {
	}

	record VerifyResetCodeRequest(@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Pattern(regexp = "\\d{6}") String code) {
	}

	record VerifyResetCodeResponse(String resetToken, Instant expiresAt) {
	}

	record ResetPasswordRequest(@NotBlank String resetToken, @NotNull String newPassword) {
	}

}
