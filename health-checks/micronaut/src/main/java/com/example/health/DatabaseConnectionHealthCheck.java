package com.example.health;

import javax.inject.Singleton;

import org.reactivestreams.Publisher;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;

@Singleton
public class DatabaseConnectionHealthCheck implements HealthIndicator {

	private boolean isDatabaseUp = true;

	@Override
	public Publisher<HealthResult> getResult() {

		HealthResult.Builder builder = HealthResult.builder("Database connection health check");

		try {
			simulateDatabaseConnectionVerification();
			builder.status(HealthStatus.UP);
		} catch (IllegalStateException e) {
			// cannot access the database
			builder.status(HealthStatus.DOWN);
		}

		return Publishers.just(builder.build());
	}

	private void simulateDatabaseConnectionVerification() {
		if (!isDatabaseUp) {
			throw new IllegalStateException("Cannot contact database");
		}
	}

}