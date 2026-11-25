package com.woorizip;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 서버 코드가 쓰는 Clock을 테스트가 돌릴 수 있는 시계로 바꾼다. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock();
	}

}
