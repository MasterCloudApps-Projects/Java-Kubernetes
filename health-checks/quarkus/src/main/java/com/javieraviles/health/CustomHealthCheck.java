package com.javieraviles.health;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class CustomHealthCheck implements HealthCheck {

	private boolean isCustomServiceUp = true;
	private String serviceName = "Custom-service";

	@Override
	public HealthCheckResponse call() {

		HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named(serviceName);

		try {
			simulateCustomServiceConnectionVerification();
			responseBuilder.up();
		} catch (IllegalStateException e) {
			responseBuilder.down();
		}

		return responseBuilder.build();
	}

	private void simulateCustomServiceConnectionVerification() {
		if (!isCustomServiceUp) {
			throw new IllegalStateException("Cannot reach custom service");
		}
	}
}