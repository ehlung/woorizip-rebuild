package com.woorizip.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** 서명 키 설정이 빠지거나 잘못되면 서버가 시작하지 않는다 (ADR 0014의 "필수 설정이 빠지면 시작하지 않는다"). */
class JwtPropertiesTest {

	// ${random.value}는 서버를 시작할 때(SpringApplication) 등록된다. 이 테스트 도구에는 없어서 직접 등록한다.
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> RandomValuePropertySource.addToEnvironment(context.getEnvironment()))
		.withUserConfiguration(JwtConfig.class)
		.withPropertyValues("woorizip.auth.jwt.issuer=woorizip", "woorizip.auth.jwt.access-token-ttl=15m");

	@Test
	void 키_설정이_있으면_시작한다() {
		this.runner
			.withPropertyValues("woorizip.auth.jwt.current-key-id=k1", "woorizip.auth.jwt.keys[0].id=k1",
					"woorizip.auth.jwt.keys[0].secret=${random.value}${random.value}")
			.run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class));
	}

	@Test
	void 키_설정이_없으면_시작하지_않는다() {
		this.runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void 키가_32바이트보다_짧으면_시작하지_않는다() {
		this.runner
			.withPropertyValues("woorizip.auth.jwt.current-key-id=k1", "woorizip.auth.jwt.keys[0].id=k1",
					"woorizip.auth.jwt.keys[0].secret=too-short-secret")
			.run(context -> assertThat(context).hasFailed());
	}

}
