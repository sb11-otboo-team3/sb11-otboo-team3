package com.otboo.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// local 프로필 전체 대신 프로퍼티만 오버라이드해 실제 DB 의존 없이 permitAll 분기를 검증한다.
// @AutoConfigureObservability: @SpringBootTest는 기본적으로 메트릭 익스포트를 꺼서 이 엔드포인트가 안 뜬다.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@TestPropertySource(properties = "security.actuator.prometheus-public=true")
class SecurityConfigPrometheusPublicTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("security.actuator.prometheus-public=true면 /actuator/prometheus를 인증 없이 접근할 수 있다")
  void prometheusEndpointPermittedWhenPropertyEnabled() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk());
  }
}
