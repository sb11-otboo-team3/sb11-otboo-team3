package com.otboo.global.infrastructure.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1000L;
    private static final long RETRY_COUNT = 2L;

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(RETRY_INTERVAL_MILLIS, RETRY_COUNT)
        );
    }
}