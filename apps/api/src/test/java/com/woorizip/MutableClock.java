package com.woorizip;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 앞으로 돌릴 수 있는 시계. 시작은 실제 현재 시각이다. */
public class MutableClock extends Clock {

	private volatile Instant now = Instant.now();

	public void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public Instant instant() {
		return this.now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
