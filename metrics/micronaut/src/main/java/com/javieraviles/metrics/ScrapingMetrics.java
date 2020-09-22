package com.javieraviles.metrics;

import java.util.Random;

import javax.inject.Singleton;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.scheduling.annotation.Scheduled;


@Singleton
public class ScrapingMetrics {

    private MeterRegistry meterRegistry;
    Random random = new Random();
    private Counter timesWebGotScrapedCounter = null;
	private Gauge timeSpentScrapingLastAttemptGauge = null;

    public ScrapingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        timesWebGotScrapedCounter = meterRegistry.counter("mn_times_website_got_scraped_counter");
    }

    @Scheduled(fixedDelay = "10s") 
    void scrapWebsite() throws InterruptedException {
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

		timeSpentScrapingLastAttemptGauge = Gauge.builder("mn_time_spent_scraping_last_attempt", this, value -> ms)
				.register(meterRegistry);
	}

}