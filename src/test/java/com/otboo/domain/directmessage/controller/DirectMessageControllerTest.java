package com.otboo.domain.directmessage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.dto.response.DirectMessageDtoCursorResponse;
import com.otboo.domain.directmessage.service.DirectMessageService;
import com.otboo.domain.user.dto.UserSummary;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DirectMessageController.class)
@Import(SecurityConfig.class)
class DirectMessageControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DirectMessageService directMessageService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @DisplayName("DM 목록 조회 성공")
  void getDirectMessages_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(currentUserId, null, List.of());

    DirectMessageDto message = new DirectMessageDto(
        messageId,
        Instant.parse("2026-07-31T00:00:00Z"),
        new UserSummary(currentUserId, "sender", null),
        new UserSummary(targetUserId, "receiver", null),
        "안녕하세요"
    );

    DirectMessageDtoCursorResponse response = new DirectMessageDtoCursorResponse(
        List.of(message),
        null,
        null,
        false,
        1L,
        "createdAt",
        "DESCENDING"
    );

    given(directMessageService.getDirectMessages(
        eq(targetUserId),
        any(),
        any(),
        eq(20),
        eq(currentUserId)
    )).willReturn(response);

    mockMvc.perform(get("/api/direct-messages")
            .param("userId", targetUserId.toString())
            .param("limit", "20")
            .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(messageId.toString()))
        .andExpect(jsonPath("$.data[0].content").value("안녕하세요"))
        .andExpect(jsonPath("$.data[0].sender.userId").value(currentUserId.toString()))
        .andExpect(jsonPath("$.data[0].receiver.userId").value(targetUserId.toString()))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
  }
}