package com.otboo.domain.follow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.UserSummary;
import com.otboo.domain.follow.service.FollowService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
class FollowControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private FollowService followService;

  @Test
  @WithMockUser
  void createFollow_success_returns201() throws Exception {
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(followerId, followeeId);
    FollowDto response = new FollowDto(
        followId,
        new UserSummary(followeeId, "followee", null),
        new UserSummary(followerId, "follower", null)
    );

    given(followService.createFollow(any(FollowCreateRequest.class))).willReturn(response);

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followee.userId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followee.name").value("followee"))
        .andExpect(jsonPath("$.follower.userId").value(followerId.toString()))
        .andExpect(jsonPath("$.follower.name").value("follower"));
  }

  @Test
  @WithMockUser
  void createFollow_missingFollowerId_returns400() throws Exception {
    // followerId가 없는 요청
    String requestBody = """
        {
          "followeeId": "%s"
        }
        """.formatted(UUID.randomUUID());

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void createFollow_missingFolloweeId_returns400() throws Exception {
    // followeeId가 없는 요청
    String requestBody = """
        {
          "followerId": "%s"
        }
        """.formatted(UUID.randomUUID());

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }
}