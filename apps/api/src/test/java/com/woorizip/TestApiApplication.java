package com.woorizip;

import org.springframework.boot.SpringApplication;

public class TestApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ApiApplication::main)
			.with(TestcontainersConfiguration.class)
			.withAdditionalProfiles("test")
			.run(args);
	}

}
