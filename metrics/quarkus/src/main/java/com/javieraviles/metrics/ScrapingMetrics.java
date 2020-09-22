package com.javieraviles.metrics;

import java.util.Random;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class ScrapingMetrics {

    Random random = new Random();
    private int timeSpentScrapingLastAttempt = 0;

    @Counted(name = "timesWebsiteGotScrapedCounter", description = "How many times the website has been scraped.")
    @Scheduled(every="10s")
    public void scrapWebsite() throws InterruptedException {
        System.out.println("Start scraping website...");
		timeSpentScrapingLastAttempt =random.nextInt(9) + 1;
		Thread.sleep(1000L * timeSpentScrapingLastAttempt);
		System.out.println("Scraping finished");
    }

    @Gauge(name = "timeSpentScrapingLastAttempt", unit = MetricUnits.SECONDS, description = "time spent scraping (last attempt)")
    public int timeSpentScrapingLastAttempt() {
        return timeSpentScrapingLastAttempt;
    }

}