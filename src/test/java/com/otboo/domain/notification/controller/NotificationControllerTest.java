package com.otboo.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.notification.dto.response.NotificationDto;
import com.otboo.domain.notification.dto.response.NotificationDtoCursorResponse;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.service.NotificationService;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.SecurityConfig;
import java.time.Instant;
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

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private NotificationService notificationService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @DisplayName("알림 목록 조회 성공 테스트")
  void getNotifications_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    NotificationDto notificationDto = new NotificationDto(
        notificationId,
        Instant.parse("2026-08-03T10:00:00Z"),
        currentUserId,
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO
    );

    NotificationDtoCursorResponse response = new NotificationDtoCursorResponse(
        List.of(notificationDto),
        null,
        null,
        false,
        1L,
        "createdAt",
        "DESCENDING"
    );

    given(notificationService.getNotifications(any(), any(), anyInt(), any()))
        .willReturn(response);

    mockMvc.perform(get("/api/notifications")
            .param("limit", "20")
            .with(authentication(mockAuthentication(currentUserId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(notificationId.toString()))
        .andExpect(jsonPath("$.data[0].receiverId").value(currentUserId.toString()))
        .andExpect(jsonPath("$.data[0].title").value("새 알림"))
        .andExpect(jsonPath("$.data[0].level").value("INFO"))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("limit 예외 테스트(1보다 작을때) 400반환해야함")
  void getNotifications_invalidLimit_returns400() throws Exception {
    UUID currentUserId = UUID.randomUUID();

    mockMvc.perform(get("/api/notifications")
            .param("limit", "0")
            .with(authentication(mockAuthentication(currentUserId))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("알림 삭제 성공 테스트 204 반환해야함")
  void deleteNotification_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(notificationService).deleteNotification(notificationId, currentUserId);
  }

  private UsernamePasswordAuthenticationToken mockAuthentication(UUID userId) {
    return new UsernamePasswordAuthenticationToken(userId, null, List.of());
  }
}