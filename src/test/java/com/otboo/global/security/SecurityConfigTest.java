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
}