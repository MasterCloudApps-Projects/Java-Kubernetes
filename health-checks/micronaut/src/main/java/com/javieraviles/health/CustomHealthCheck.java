package com.javieraviles.health;

import javax.inject.Singleton;

import org.reactivestreams.Publisher;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;

@Singleton
public class CustomHealthCheck implements HealthIndicator {

	private boolean isCustomServiceUp = true;
	private String serviceName = "Custom-service";

	@Override
	public Publisher<HealthResult> getResult() {

		HealthResult.Builder builder = HealthResult.builder(serviceName);

		try {
			simulateCustomServiceConnectionVerification();
			builder.status(HealthStatus.UP);
		} catch (IllegalStateException e) {
			builder.status(HealthStatus.DOWN);
		}

		return Publishers.just(builder.build());
	}

	private void simulateCustomServiceConnectionVerification() {
		if (!isCustomServiceUp) {
			throw new IllegalStateException("Cannot reach custom service");
		}
	}

}