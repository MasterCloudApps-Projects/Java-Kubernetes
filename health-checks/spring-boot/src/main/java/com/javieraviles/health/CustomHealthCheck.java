package com.javieraviles.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CustomHealthCheck implements HealthIndicator {

	private boolean isCustomServiceUp = true;
	private String serviceName = "Custom-service";

	@Override
	public Health health() {
		try {
			simulateCustomServiceConnectionVerification();
			return Health.up().withDetail(serviceName, "Available").build();
		} catch (IllegalStateException e) {
			return Health.down().withDetail(serviceName, e.getMessage()).build();
		}
	}

	private void simulateCustomServiceConnectionVerification() {
		if (!isCustomServiceUp) {
			throw new IllegalStateException("Cannot reach custom service");
		}
	}

}
