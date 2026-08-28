package com.otboo.domain.weather.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class WeatherPrefetchProducerTest {

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  private ObjectMapper objectMapper;
  private WeatherPrefetchProducer weatherPrefetchProducer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    weatherPrefetchProducer = new WeatherPrefetchProducer(kafkaTemplate, objectMapper);
  }

  @Test
  @DisplayName("격자 프리페치 요청 메시지를 Kafka topic으로 발행한다")
  void send_success() throws Exception {
    GridForecastRequestedMessage message = new GridForecastRequestedMessage(60, 127);

    CompletableFuture<SendResult<String, String>> future =
        CompletableFuture.completedFuture(null);

    given(kafkaTemplate.send(
        eq(WeatherKafkaTopics.GRID_FORECAST_REQUESTED),
        eq("60,127"),
        anyString()
    )).willReturn(future);

    weatherPrefetchProducer.send(message);

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    verify(kafkaTemplate).send(
        eq(WeatherKafkaTopics.GRID_FORECAST_REQUESTED),
        eq("60,127"),
        payloadCaptor.capture()
    );

    GridForecastRequestedMessage payload =
        objectMapper.readValue(payloadCaptor.getValue(), GridForecastRequestedMessage.class);

    assertThat(payload.x()).isEqualTo(60);
    assertThat(payload.y()).isEqualTo(127);
  }

  @Test
  @DisplayName("Kafka 발행 실패 시 예외를 던진다")
  void send_fail_throwsException() {
    GridForecastRequestedMessage message = new GridForecastRequestedMessage(60, 127);

    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("Kafka failure"));

    given(kafkaTemplate.send(
        eq(WeatherKafkaTopics.GRID_FORECAST_REQUESTED),
        eq("60,127"),
        anyString()
    )).willReturn(future);

    assertThatThrownBy(() -> weatherPrefetchProducer.send(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("날씨 프리페치 Kafka 메시지 발행 실패");
  }

  @Test
  @DisplayName("JSON 직렬화 자체가 실패하면 발행을 시도하지 않고 예외를 던진다")
  void send_serializationFails_throwsExceptionWithoutPublishing() throws Exception {
    ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
    given(failingObjectMapper.writeValueAsString(any()))
        .willThrow(new JsonProcessingException("직렬화 실패") {});
    WeatherPrefetchProducer producer = new WeatherPrefetchProducer(kafkaTemplate, failingObjectMapper);

    GridForecastRequestedMessage message = new GridForecastRequestedMessage(60, 127);

    assertThatThrownBy(() -> producer.send(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("날씨 프리페치 Kafka 메시지 직렬화 실패");

    verifyNoInteractions(kafkaTemplate);
  }
}
