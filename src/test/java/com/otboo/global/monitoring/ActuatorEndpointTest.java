package com.otboo.global.monitoring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

// @SpringBootTest는 기본적으로 메트릭 익스포트를 꺼버려서 PrometheusMeterRegistry가 안 뜬다 - 다시 켠다.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Health Check 엔드포인트는 애플리케이션 상태만 반환한다")
    void healthEndPointReturnsOnlyStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("노출하지 않은 Actuator 엔드포인트에는 접근할 수 없다")
    void unexposedActuatorIsNotAccessible() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    // local 프로필의 permitAll 자체는 검증하지 않음 - 실제 Postgres/Redis가 필요해서 CI에서 못 돌림
    @Test
    @DisplayName("prometheus 엔드포인트는 local 프로필이 아니면 인증 없이 접근할 수 없다")
    void prometheusEndpointRequiresAuthenticationOutsideLocalProfile() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("인증된 요청이면 prometheus 엔드포인트가 실제로 노출되어 텍스트 포맷으로 응답한다")
    void prometheusEndpointRespondsWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
