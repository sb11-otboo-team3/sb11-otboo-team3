package com.otboo.domain.weather.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.exception.KakaoApiException;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.service.WeatherService;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

@WebMvcTest(WeatherControllerImpl.class)
class WeatherControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private WeatherService weatherService;

  @Nested
  @DisplayName("GET /api/weathers/location")
  class GetLocation {

    @Test
    @DisplayName("위경도로 날씨 위치 정보를 조회하면 200과 위치정보를 반환한다")
    @WithMockUser
    void returns200AndLocationWhenQueriedByLatLng() throws Exception {
      // given
      WeatherAPILocation response = new WeatherAPILocation(
          37.5665, 126.9780, 60, 127,
          new String[]{"서울특별시", "강서구", "마곡동"}
      );
      given(weatherService.getLocation(37.5665, 126.9780)).willReturn(Mono.just(response));

      // when & then
      // 컨트롤러가 Mono를 리턴하면 MockMvc는 비동기 요청으로 처리한다 - 응답이 도착할 때까지
      // 요청 스레드가 대기하는 게 아니라, asyncDispatch로 "결과 도착 후" 단계를 한 번 더 밟아야 한다.
      MvcResult mvcResult = mockMvc.perform(get("/api/weathers/location")
              .param("longitude", "126.9780")
              .param("latitude", "37.5665")
              .with(csrf()))
          .andExpect(request().asyncStarted())
          .andReturn();

      mockMvc.perform(asyncDispatch(mvcResult))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.latitude").
              value(37.5665))
          .andExpect(jsonPath("$.longitude").value(126.9780))
          .andExpect(jsonPath("$.x").value(60))
          .andExpect(jsonPath("$.y").value(127))
          .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"))
          .andExpect(jsonPath("$.locationNames[1]").value("강서구"))
          .andExpect(jsonPath("$.locationNames[2]").value("마곡동"));
    }

    @Test
    @DisplayName("Mono가 카카오 호출 실패로 에러 완료되면, 비동기 디스패치 이후에도 GlobalExceptionHandler가 잡아 400을 반환한다")
    @WithMockUser
    void returns400WhenLocationMonoCompletesWithError() throws Exception {
      // given
      given(weatherService.getLocation(37.5665, 126.9780))
          .willReturn(Mono.error(new KakaoApiException(37.5665, 126.9780, new RuntimeException("카카오 장애"))));

      // when & then
      MvcResult mvcResult = mockMvc.perform(get("/api/weathers/location")
              .param("longitude", "126.9780")
              .param("latitude", "37.5665")
              .with(csrf()))
          .andExpect(request().asyncStarted())
          .andReturn();

      mockMvc.perform(asyncDispatch(mvcResult))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("KakaoApiException"));
    }

    @Test
    @DisplayName("longitude가 없으면 400을 반환한다")
    @WithMockUser
    void returns400WhenLongitudeIsMissing() throws Exception {
      // when & then
      mockMvc.perform(get("/api/weathers/location")
              .param("latitude", "37.5665"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("latitude가 없으면 400을 반환한다")
    @WithMockUser
    void returns400WhenLatitudeIsMissing() throws Exception {
      // when & then
      mockMvc.perform(get("/api/weathers/location")
              .param("longitude", "126.9780"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("GET /api/weathers")
  class GetWeathers {

    @Test
    @DisplayName("위경도로 날씨 정보를 조회하면 200과 예보 목록을 반환한다")
    @WithMockUser
    void returns200AndWeatherListWhenQueriedByLatLng() throws Exception {
      // given
      WeatherAPILocation location = new WeatherAPILocation(
          37.5665, 126.9780, 60, 127, new String[]{"서울특별시", "강서구", "마곡동"});
      WeatherDto weather = new WeatherDto(
          UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
          Instant.parse("2026-07-30T00:00:00Z"),
          Instant.parse("2026-07-30T09:00:00Z"),
          location,
          SkyStatus.CLEAR,
          new PrecipitationDto(PrecipitationType.NONE, 0.0, 10.0),
          new HumidityDto(55.0, -3.0),
          new TemperatureDto(23.5, 1.2, 18.0, 26.0),
          new WindSpeedDto(2.3, WindStrength.WEAK)
      );
      given(weatherService.getWeathers(37.5665, 126.9780)).willReturn(Mono.just(List.of(weather)));

      // when & then
      MvcResult mvcResult = mockMvc.perform(get("/api/weathers")
              .param("latitude", "37.5665")
              .param("longitude", "126.9780")
              .with(csrf()))
          .andExpect(request().asyncStarted())
          .andReturn();

      mockMvc.perform(asyncDispatch(mvcResult))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].skyStatus").value("CLEAR"))
          .andExpect(jsonPath("$[0].location.locationNames[2]").value("마곡동"))
          .andExpect(jsonPath("$[0].precipitation.type").value("NONE"))
          .andExpect(jsonPath("$[0].windSpeed.asWord").value("WEAK"));
    }

    @Test
    @DisplayName("Mono가 기상청 호출 실패로 에러 완료되면, 비동기 디스패치 이후에도 GlobalExceptionHandler가 잡아 400을 반환한다")
    @WithMockUser
    void returns400WhenWeathersMonoCompletesWithError() throws Exception {
      // given
      VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
      given(weatherService.getWeathers(37.5665, 126.9780))
          .willReturn(Mono.error(new KmaApiException(60, 127, baseTime, new RuntimeException("기상청 장애"))));

      // when & then
      MvcResult mvcResult = mockMvc.perform(get("/api/weathers")
              .param("latitude", "37.5665")
              .param("longitude", "126.9780")
              .with(csrf()))
          .andExpect(request().asyncStarted())
          .andReturn();

      mockMvc.perform(asyncDispatch(mvcResult))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("KmaApiException"));
    }

    @Test
    @DisplayName("longitude가 없으면 400을 반환한다")
    @WithMockUser
    void returns400WhenLongitudeIsMissing() throws Exception {
      // when & then
      mockMvc.perform(get("/api/weathers")
              .param("latitude", "37.5665"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("latitude가 없으면 400을 반환한다")
    @WithMockUser
    void returns400WhenLatitudeIsMissing() throws Exception {
      // when & then
      mockMvc.perform(get("/api/weathers")
              .param("longitude", "126.9780"))
          .andExpect(status().isBadRequest());
    }
  }
}