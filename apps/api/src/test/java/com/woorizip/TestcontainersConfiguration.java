package com.woorizip;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// 로컬 Docker Compose(infra/docker-compose.yml)와 같은 이미지를 쓴다 (ADR 0016).
	static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
		.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}

}
