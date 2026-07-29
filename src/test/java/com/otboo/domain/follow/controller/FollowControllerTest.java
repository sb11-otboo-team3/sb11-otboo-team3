package com.otboo.domain.follow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.dto.response.UserSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import com.otboo.domain.follow.exception.FollowNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
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
  @DisplayName("팔로우 생성에 성공 시 201과 FollowDto를 반환 테스트")
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
  @DisplayName("followerId가 없을 시 400반환 테스트")
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
  @DisplayName("followeeId가 없을 시 400반환 테스트")
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

  @Test
  @WithMockUser
  @DisplayName("팔로우 취소 성공 시 204를 반환 테스트")
  void cancelFollow_success_returns204() throws Exception {
    UUID followId = UUID.randomUUID();

    mockMvc.perform(delete("/api/follows/{followId}", followId)
            .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser
  @DisplayName("존재하지 않는 팔로우를 취소 시 400을 반환 테스트")
  void cancelFollow_notFound_returns400() throws Exception {
    UUID followId = UUID.randomUUID();

    willThrow(new FollowNotFoundException(followId))
        .given(followService)
        .cancelFollow(followId);

    mockMvc.perform(delete("/api/follows/{followId}", followId)
            .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"));
  }

  @Test
  @WithMockUser
  @DisplayName("팔로잉 목록 조회 성공 테스트")
  void getFollowings_success_returns200() throws Exception {
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowListResponse response = new FollowListResponse(
        List.of(
            new FollowDto(
                followId,
                new UserSummary(followeeId, "followee", null),
                new UserSummary(followerId, "follower", null)
            )
        ),
        "followee",
        followId,
        true,
        10L,
        "name",
        "ASCENDING"
    );

    given(followService.getFollowings(
        eq(followerId),
        isNull(),
        isNull(),
        eq(20),
        isNull()
    )).willReturn(response);

    mockMvc.perform(get("/api/follows/followings")
            .param("followerId", followerId.toString())
            .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(followId.toString()))
        .andExpect(jsonPath("$.data[0].followee.userId").value(followeeId.toString()))
        .andExpect(jsonPath("$.data[0].followee.name").value("followee"))
        .andExpect(jsonPath("$.data[0].follower.userId").value(followerId.toString()))
        .andExpect(jsonPath("$.data[0].follower.name").value("follower"))
        .andExpect(jsonPath("$.nextCursor").value("followee"))
        .andExpect(jsonPath("$.nextIdAfter").value(followId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(10))
        .andExpect(jsonPath("$.sortBy").value("name"))
        .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
  }

  @Test
  @WithMockUser
  @DisplayName("limit이 1보다 작아 실패 시 400 반환 테스트")
  void getFollowings_invalidLimit_returns400() throws Exception {
    UUID followerId = UUID.randomUUID();

    mockMvc.perform(get("/api/follows/followings")
            .param("followerId", followerId.toString())
            .param("limit", "0"))
        .andExpect(status().isBadRequest());

    verify(followService, never()).getFollowings(
        any(),
        any(),
        any(),
        anyInt(),
        any()
    );
  }
}