package org.acme.health;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class DatabaseConnectionHealthCheck implements HealthCheck {

	private boolean isDatabaseUp = true;

	@Override
	public HealthCheckResponse call() {

		HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("Database connection health check");

		try {
			simulateDatabaseConnectionVerification();
			responseBuilder.up();
		} catch (IllegalStateException e) {
			// cannot access the database
			responseBuilder.down();
		}

		return responseBuilder.build();
	}

	private void simulateDatabaseConnectionVerification() {
		if (!isDatabaseUp) {
			throw new IllegalStateException("Cannot contact database");
		}
	}
}