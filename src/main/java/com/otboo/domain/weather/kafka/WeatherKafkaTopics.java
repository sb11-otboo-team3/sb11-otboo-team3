package com.otboo.domain.weather.kafka;

public final class WeatherKafkaTopics {

  public static final String GRID_FORECAST_REQUESTED = "otboo.weather.prefetch-requested.v1";
  // 공통 KafkaConsumerConfig가 실패 메시지를 "원본 토픽명 + .dlt"로 보낸다(DLT_SUFFIX 참고).
  public static final String GRID_FORECAST_REQUESTED_DLT = GRID_FORECAST_REQUESTED + ".dlt";

  private WeatherKafkaTopics() {
  }
}
