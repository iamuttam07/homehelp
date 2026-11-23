package com.uel.homehelp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway PostGIS database and Redis for tests. {@code @ServiceConnection}
 * points the app at them automatically. Requires Docker to be running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	static final DockerImageName POSTGIS_IMAGE =
			DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGIS_IMAGE);
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
	}

}
