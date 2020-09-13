package com.javieraviles.metrics;

import java.util.Random;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

@Component
public class ScrapingMetrics implements MeterBinder {

	Random random = new Random();
	private MeterRegistry meterRegistry = null;
	private Counter timesWebGotScrapedCounter = null;
	private Gauge timeSpentScrapingLastAttemptGauge = null;

	@Override
	public void bindTo(final MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		timesWebGotScrapedCounter = meterRegistry.counter("times_website_got_scraped_counter");
	}

	@Scheduled(fixedRate = 10000)
	@Timed(description = "Timer to scrap website")
	public void scrapWebsite() throws InterruptedException {
		System.out.println("Start scraping website...");
		int ms = random.nextInt(9) + 1;
		setTimeScrapingLastAttemptMetric(ms);
		Thread.sleep(1000L * ms);
		timesWebGotScrapedCounter.increment();
		System.out.println("Scraping finished");
	}

	private void setTimeScrapingLastAttemptMetric(int ms) {
		if (timeSpentScrapingLastAttemptGauge != null) {
			meterRegistry.remove(timeSpentScrapingLastAttemptGauge);
		}

		timeSpentScrapingLastAttemptGauge = Gauge.builder("time_spent_scraping_last_attempt", this, value -> ms)
				.register(meterRegistry);
	}
}
