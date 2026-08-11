package com.otboo.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.notification.service.NotificationService;
import com.otboo.domain.notification.sse.SseController;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(SseController.class)
@Import(SecurityConfig.class)
class SseControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private NotificationService notificationService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @DisplayName("SSE 구독 성공 테스트")
  void subscribe_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(1000L);

    given(notificationService.subscribe(any(), any()))
        .willReturn(emitter);

    mockMvc.perform(get("/api/sse")
            .with(authentication(mockAuthentication(currentUserId))))
        .andExpect(status().isOk());

    verify(notificationService).subscribe(currentUserId, null);
  }

  private UsernamePasswordAuthenticationToken mockAuthentication(UUID userId) {
    return new UsernamePasswordAuthenticationToken(userId, null, List.of());
  }
}