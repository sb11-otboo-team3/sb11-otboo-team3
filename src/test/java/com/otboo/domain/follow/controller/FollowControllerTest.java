package com.otboo.domain.follow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import com.otboo.domain.follow.dto.response.UserSummary;
import com.otboo.domain.follow.exception.FollowNotFoundException;
import com.otboo.domain.follow.service.FollowService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(FollowController.class)
class FollowControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private FollowService followService;

  @Test
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

    given(followService.createFollow(any(FollowCreateRequest.class), any(UUID.class)))
        .willReturn(response);

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .with(authenticatedUser(followerId))
            .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followee.userId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followee.name").value("followee"))
        .andExpect(jsonPath("$.follower.userId").value(followerId.toString()))
        .andExpect(jsonPath("$.follower.name").value("follower"));

    verify(followService).createFollow(any(FollowCreateRequest.class), eq(followerId));
  }

  @Test
  @DisplayName("followerId가 없을 시 400반환 테스트")
  void createFollow_missingFollowerId_returns400() throws Exception {
    String requestBody = """
        {
          "followeeId": "%s"
        }
        """.formatted(UUID.randomUUID());

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody)
            .with(authenticatedUser(UUID.randomUUID()))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("followeeId가 없을 시 400반환 테스트")
  void createFollow_missingFolloweeId_returns400() throws Exception {
    String requestBody = """
        {
          "followerId": "%s"
        }
        """.formatted(UUID.randomUUID());

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody)
            .with(authenticatedUser(UUID.randomUUID()))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("팔로우 취소 성공 시 204를 반환 테스트")
  void cancelFollow_success_returns204() throws Exception {
    UUID followId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    mockMvc.perform(delete("/api/follows/{followId}", followId)
            .with(authenticatedUser(currentUserId))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(followService).cancelFollow(followId, currentUserId);
  }

  @Test
  @DisplayName("존재하지 않는 팔로우를 취소 시 400을 반환 테스트")
  void cancelFollow_notFound_returns400() throws Exception {
    UUID followId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    willThrow(new FollowNotFoundException(followId))
        .given(followService)
        .cancelFollow(followId, currentUserId);

    mockMvc.perform(delete("/api/follows/{followId}", followId)
            .with(authenticatedUser(currentUserId))
            .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"));

    verify(followService).cancelFollow(followId, currentUserId);
  }

  @Test
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
            .param("limit", "20")
            .with(authenticatedUser(UUID.randomUUID())))
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
  @DisplayName("limit이 1보다 작아 실패 시 400 반환 테스트")
  void getFollowings_invalidLimit_returns400() throws Exception {
    UUID followerId = UUID.randomUUID();

    mockMvc.perform(get("/api/follows/followings")
            .param("followerId", followerId.toString())
            .param("limit", "0")
            .with(authenticatedUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest());

    verify(followService, never()).getFollowings(
        any(),
        any(),
        any(),
        anyInt(),
        any()
    );
  }

  @Test
  @DisplayName("팔로워 목록 조회 성공 테스트")
  void getFollowers_success_returns200() throws Exception {
    UUID followId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();

    FollowListResponse response = new FollowListResponse(
        List.of(
            new FollowDto(
                followId,
                new UserSummary(followeeId, "followee", null),
                new UserSummary(followerId, "follower", null)
            )
        ),
        "follower",
        followId,
        true,
        10L,
        "name",
        "ASCENDING"
    );

    given(followService.getFollowers(
        eq(followeeId),
        isNull(),
        isNull(),
        eq(20),
        isNull()
    )).willReturn(response);

    mockMvc.perform(get("/api/follows/followers")
            .param("followeeId", followeeId.toString())
            .param("limit", "20")
            .with(authenticatedUser(UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(followId.toString()))
        .andExpect(jsonPath("$.data[0].followee.userId").value(followeeId.toString()))
        .andExpect(jsonPath("$.data[0].followee.name").value("followee"))
        .andExpect(jsonPath("$.data[0].follower.userId").value(followerId.toString()))
        .andExpect(jsonPath("$.data[0].follower.name").value("follower"))
        .andExpect(jsonPath("$.nextCursor").value("follower"))
        .andExpect(jsonPath("$.nextIdAfter").value(followId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(10))
        .andExpect(jsonPath("$.sortBy").value("name"))
        .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
  }

  @Test
  @DisplayName("limit이 1보다 작아 실패 시 400 반환 테스트")
  void getFollowers_invalidLimit_returns400() throws Exception {
    UUID followeeId = UUID.randomUUID();

    mockMvc.perform(get("/api/follows/followers")
            .param("followeeId", followeeId.toString())
            .param("limit", "0")
            .with(authenticatedUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest());

    verify(followService, never()).getFollowers(
        any(),
        any(),
        any(),
        anyInt(),
        any()
    );
  }

  private RequestPostProcessor authenticatedUser(UUID userId) {
    return authentication(
        new UsernamePasswordAuthenticationToken(userId, null, List.of())
    );
  }

  @Test
  @DisplayName("팔로우 요약 조회 성공 테스트")
  void getFollowSummary_success_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID followedByMeId = UUID.randomUUID();

    FollowSummaryDto response = new FollowSummaryDto(
        userId,
        5L,
        3L,
        true,
        followedByMeId,
        true
    );

    given(followService.getFollowSummary(userId, currentUserId))
        .willReturn(response);

    mockMvc.perform(get("/api/follows/summary")
            .param("userId", userId.toString())
            .with(authenticatedUser(currentUserId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followeeId").value(userId.toString()))
        .andExpect(jsonPath("$.followerCount").value(5))
        .andExpect(jsonPath("$.followingCount").value(3))
        .andExpect(jsonPath("$.followedByMe").value(true))
        .andExpect(jsonPath("$.followedByMeId").value(followedByMeId.toString()))
        .andExpect(jsonPath("$.followingMe").value(true));

    verify(followService).getFollowSummary(userId, currentUserId);
  }

  @Test
  @DisplayName("userId가 없을때 400 반환 실패 테스트")
  void getFollowSummary_missingUserId_returns400() throws Exception {
    UUID currentUserId = UUID.randomUUID();

    mockMvc.perform(get("/api/follows/summary")
            .with(authenticatedUser(currentUserId)))
        .andExpect(status().isBadRequest());

    verify(followService, never()).getFollowSummary(any(), any());
  }
}