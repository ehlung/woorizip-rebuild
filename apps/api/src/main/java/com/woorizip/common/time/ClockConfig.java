package com.woorizip.common.time;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

	/**
	 * 서버가 쓰는 현재 시각. 마이크로초 단위로 끊는다.
	 *
	 * <p>PostgreSQL의 timestamptz는 마이크로초까지만 저장한다. 나노초가 있는 시각으로 만료 시각을 계산해 저장하면
	 * 저장된 값이 계산한 값과 달라져, 만료 시각과 같은 순간의 판정이 바뀐다(Linux CI에서 실제로 겪음).
	 */
	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
	}

}
