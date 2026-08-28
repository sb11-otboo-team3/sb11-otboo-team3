package com.otboo.domain.weather.diff;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WeatherDiffProperties.class)
public class WeatherDiffConfig {
}
