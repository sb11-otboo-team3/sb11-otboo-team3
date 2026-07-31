package com.otboo.domain.weather.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WeatherControllerImpl.class)
class WeatherControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private WeatherService weatherService;

  @Test
  @DisplayName("위경도로 날씨 위치 정보를 조회하면 200과 위치정보를 반환한다")
  @WithMockUser
  void returns200AndLocationWhenQueriedByLatLng() throws Exception {
    // given
    WeatherAPILocation response = new WeatherAPILocation(
        37.5665, 126.9780, 60, 127,
        new String[]{"서울특별시", "강서구", "마곡동"}
    );
    given(weatherService.getLocation(37.5665, 126.9780)).willReturn(response);

    // when & then
    mockMvc.perform(get("/api/weathers/location")
            .param("longitude", "126.9780")
            .param("latitude", "37.5665")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latitude").value(37.5665))
        .andExpect(jsonPath("$.longitude").value(126.9780))
        .andExpect(jsonPath("$.x").value(60))
        .andExpect(jsonPath("$.y").value(127))
        .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"))
        .andExpect(jsonPath("$.locationNames[1]").value("강서구"))
        .andExpect(jsonPath("$.locationNames[2]").value("마곡동"));
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