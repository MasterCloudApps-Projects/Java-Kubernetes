package com.example.demo.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionHealthCheck implements HealthIndicator {

	private boolean isDatabaseUp = true;
	private String serviceName = "Database-connection";

	@Override
	public Health health() {
		try {
			simulateDatabaseConnectionVerification();
			return Health.up().withDetail(serviceName, "Available").build();
		} catch (IllegalStateException e) {
			// cannot access the database
			return Health.down().withDetail(serviceName, e.getMessage()).build();
		}
	}

	private void simulateDatabaseConnectionVerification() {
		if (!isDatabaseUp) {
			throw new IllegalStateException("Cannot contact database");
		}
	}

}
