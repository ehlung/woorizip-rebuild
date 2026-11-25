package com.woorizip.auth.application;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

	@Bean
	JwtKeys jwtKeys(JwtProperties properties) {
		return new JwtKeys(properties);
	}

	@Bean
	JwtEncoder jwtEncoder(JwtKeys keys) {
		return keys.encoder();
	}

	/** Spring Security가 요청의 Bearer 토큰을 검증할 때 쓴다. */
	@Bean
	JwtDecoder jwtDecoder(JwtKeys keys) {
		return keys.decoder();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
