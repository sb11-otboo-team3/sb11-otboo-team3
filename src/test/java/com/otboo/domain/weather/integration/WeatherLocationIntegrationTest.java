package com.otboo.domain.weather.integration;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.exception.KakaoApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeatherLocationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private KakaoLocationClient kakaoLocationClient;

  @Test
  @DisplayName("잘못된 위경도로 요청하면 실제 서비스/GridConverter를 거쳐 400을 반환한다")
  @WithMockUser
  void returns400ForInvalidLatLng() throws Exception {
    mockMvc.perform(get("/api/weathers/location")
            .param("latitude", "999")
            .param("longitude", "126.9780"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("카카오 API 호출이 실패하면 실제 서비스를 거쳐 400을 반환한다")
  @WithMockUser
  void returns400WhenKakaoApiFails() throws Exception {
    // given
    double latitude = 35.1796;
    double longitude = 129.0756;
    given(kakaoLocationClient.getRegion(latitude, longitude))
        .willThrow(new KakaoApiException(latitude, longitude, new RuntimeException("카카오 서버 장애")));

    // when & then
    mockMvc.perform(get("/api/weathers/location")
            .param("latitude", String.valueOf(latitude))
            .param("longitude", String.valueOf(longitude)))
        .andExpect(status().isBadRequest());
  }
}
