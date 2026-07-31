package com.otboo.domain.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
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
    assertThat(item.precipitationAmountRaw()).isEqualTo("강수없음");
    assertThat(item.precipitationProbability()).isEqualTo(20.0);
    assertThat(item.humidity()).isEqualTo(55.0);
    assertThat(item.temperature()).isEqualTo(23.0);
    assertThat(item.windSpeed()).isEqualTo(2.3);
    assertThat(item.windStrength()).isEqualTo(WindStrength.WEAK);
  }
}
