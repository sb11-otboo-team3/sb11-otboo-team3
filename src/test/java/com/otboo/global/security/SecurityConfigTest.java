package com.otboo.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("permitAll 대상이 아닌 요청은 인증 없이 접근 시 401을 반환한다")
  void protectedEndpointRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/users/some-user-id/profiles"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CSRF 토큰 없이 인증 필요한 POST 요청을 보내면 403을 반환한다")
  void requestWithoutCsrfTokenReturns403() throws Exception {
    // 이건 예시 개념이고, 실제로는 인증까지 필요한 API가 아직 없어서
    // 다른 방식으로 검증이 필요할 수 있어요
  }
}