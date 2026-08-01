package com.otboo.domain.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaWeatherClientTest {

  private MockRestServiceServer mockServer;
  private KmaWeatherClient kmaWeatherClient;

  @BeforeEach
  void setUp() {
    RestClient.Builder restClientBuilder = RestClient.builder()
        .baseUrl("https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0");
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    kmaWeatherClient = new KmaWeatherClient(restClientBuilder.build(), "test-api-key");
  }

  @Test
  @DisplayName("격자와 발표시각으로 조회하면 예보시각별로 조립된 예보 목록을 반환한다")
  void returnsForecastItemsGroupedByForecastTime() {
    // given
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    String responseBody = """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" },
            "body": {
              "dataType": "JSON",
              "items": {
                "item": [
                  { "baseDate": "20260730", "baseTime": "0500", "category": "TMP", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "23", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "SKY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "1",  "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PTY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "0",  "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "POP", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "20", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PCP", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "강수없음", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "REH", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "55", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "WSD", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "2.3", "nx": 60, "ny": 127 }
                ]
              },
              "pageNo": 1, "numOfRows": 1000, "totalCount": 7
            }
          }
        }
        """;

    mockServer.expect(requestTo(
            "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?authKey=test-api-key&numOfRows=1000&pageNo=1&dataType=JSON"
                + "&base_date=20260730&base_time=0500&nx=60&ny=127"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when
    List<VilageFcstItem> result = kmaWeatherClient.getForecast(60, 127, baseTime);

    // then
    assertThat(result).hasSize(1);
    VilageFcstItem item = result.get(0);
    assertThat(item.forecastedAt()).isEqualTo(LocalDateTime.of(2026, 7, 30, 5, 0));
    assertThat(item.forecastAt()).isEqualTo(LocalDateTime.of(2026, 7, 30, 9, 0));
    assertThat(item.skyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(item.precipitationType()).isEqualTo(PrecipitationType.NONE);
    assertThat(item.precipitationAmount()).isEqualTo(0.0);
    assertThat(item.precipitationProbability()).isEqualTo(20.0);
    assertThat(item.humidity()).isEqualTo(55.0);
    assertThat(item.temperature()).isEqualTo(23.0);
    assertThat(item.windSpeed()).isEqualTo(2.3);
    assertThat(item.windStrength()).isEqualTo(WindStrength.WEAK);
  }

  @Test
  @DisplayName("여러 예보시각이 섞여 있으면 시각별로 정확히 그룹핑한다")
  void groupsMultipleForecastSlotsByForecastTime() {
    // given
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    String responseBody = """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" },
            "body": {
              "dataType": "JSON",
              "items": {
                "item": [
                  { "baseDate": "20260730", "baseTime": "0500", "category": "TMP", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "18", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "SKY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "3", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PTY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "1", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "TMP", "fcstDate": "20260730", "fcstTime": "1200", "fcstValue": "22", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "SKY", "fcstDate": "20260730", "fcstTime": "1200", "fcstValue": "4", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PTY", "fcstDate": "20260730", "fcstTime": "1200", "fcstValue": "2", "nx": 60, "ny": 127 }
                ]
              },
              "pageNo": 1, "numOfRows": 1000, "totalCount": 6
            }
          }
        }
        """;

    mockServer.expect(requestTo(
            "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?authKey=test-api-key&numOfRows=1000&pageNo=1&dataType=JSON"
                + "&base_date=20260730&base_time=0500&nx=60&ny=127"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when
    List<VilageFcstItem> result = kmaWeatherClient.getForecast(60, 127, baseTime);

    // then
    assertThat(result).hasSize(2);

    VilageFcstItem slot0900 = result.stream()
        .filter(item -> item.forecastAt().equals(LocalDateTime.of(2026, 7, 30, 9, 0)))
        .findFirst()
        .orElseThrow();
    assertThat(slot0900.temperature()).isEqualTo(18.0);
    assertThat(slot0900.skyStatus()).isEqualTo(SkyStatus.MOSTLY_CLOUDY);
    assertThat(slot0900.precipitationType()).isEqualTo(PrecipitationType.RAIN);

    VilageFcstItem slot1200 = result.stream()
        .filter(item -> item.forecastAt().equals(LocalDateTime.of(2026, 7, 30, 12, 0)))
        .findFirst()
        .orElseThrow();
    assertThat(slot1200.temperature()).isEqualTo(22.0);
    assertThat(slot1200.skyStatus()).isEqualTo(SkyStatus.CLOUDY);
    assertThat(slot1200.precipitationType()).isEqualTo(PrecipitationType.RAIN_SNOW);
  }

  @Test
  @DisplayName("나머지 강수형태 코드값(눈, 소나기)도 정확히 매핑한다")
  void mapsRemainingPrecipitationTypeCodes() {
    // given
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    String responseBody = """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" },
            "body": {
              "dataType": "JSON",
              "items": {
                "item": [
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PTY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "3", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PTY", "fcstDate": "20260730", "fcstTime": "1200", "fcstValue": "4", "nx": 60, "ny": 127 }
                ]
              },
              "pageNo": 1, "numOfRows": 1000, "totalCount": 2
            }
          }
        }
        """;

    mockServer.expect(requestTo(
            "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?authKey=test-api-key&numOfRows=1000&pageNo=1&dataType=JSON"
                + "&base_date=20260730&base_time=0500&nx=60&ny=127"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when
    List<VilageFcstItem> result = kmaWeatherClient.getForecast(60, 127, baseTime);

    // then
    VilageFcstItem slot0900 = result.stream()
        .filter(item -> item.forecastAt().equals(LocalDateTime.of(2026, 7, 30, 9, 0)))
        .findFirst()
        .orElseThrow();
    assertThat(slot0900.precipitationType()).isEqualTo(PrecipitationType.SNOW);

    VilageFcstItem slot1200 = result.stream()
        .filter(item -> item.forecastAt().equals(LocalDateTime.of(2026, 7, 30, 12, 0)))
        .findFirst()
        .orElseThrow();
    assertThat(slot1200.precipitationType()).isEqualTo(PrecipitationType.SHOWER);
  }

  @Test
  @DisplayName("알 수 없는 코드값이면 null로 매핑한다")
  void mapsUnknownCodesToNull() {
    // given
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    String responseBody = """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" },
            "body": {
              "dataType": "JSON",
              "items": {
                "item": [
                  { "baseDate": "20260730", "baseTime": "0500", "category": "SKY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "9", "nx": 60, "ny": 127 },
                  { "baseDate": "20260730", "baseTime": "0500", "category": "PTY", "fcstDate": "20260730", "fcstTime": "0900", "fcstValue": "9", "nx": 60, "ny": 127 }
                ]
              },
              "pageNo": 1, "numOfRows": 1000, "totalCount": 2
            }
          }
        }
        """;

    mockServer.expect(requestTo(
            "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?authKey=test-api-key&numOfRows=1000&pageNo=1&dataType=JSON"
                + "&base_date=20260730&base_time=0500&nx=60&ny=127"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when
    List<VilageFcstItem> result = kmaWeatherClient.getForecast(60, 127, baseTime);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).skyStatus()).isNull();
    assertThat(result.get(0).precipitationType()).isNull();
  }

  @Test
  @DisplayName("기상청 API 호출이 실패하면 KmaApiException을 던진다")
  void throwsKmaApiExceptionWhenCallFails() {
    // given
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));

    mockServer.expect(requestTo(
            "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?authKey=test-api-key&numOfRows=1000&pageNo=1&dataType=JSON"
                + "&base_date=20260730&base_time=0500&nx=60&ny=127"))
        .andRespond(withServerError());

    // when & then
    assertThatThrownBy(() -> kmaWeatherClient.getForecast(60, 127, baseTime))
        .isInstanceOf(KmaApiException.class);
  }
}
