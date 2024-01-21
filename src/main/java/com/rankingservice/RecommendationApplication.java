package com.rankingservice;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.metrics.cache.CacheMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {CacheMetricsAutoConfiguration.class})
@EnableAsync
@EnableCaching
@EnableFeignClients
@EnableScheduling
@ComponentScan(basePackages = {"cn","com"})

public class RecommendationApplication {

	@Bean
	MeterRegistryCustomizer<MeterRegistry> configurer(){
		return registry -> registry.config().commonTags("application", "bid-server");
	}

	public static void main(String[] args) {
		SpringApplication.run(RecommendationApplication.class, args);
	}

}
