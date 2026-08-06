package com.otboo.domain.feed.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.response.FeedCommentDto;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.service.FeedService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeedController.class)
class FeedControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private FeedService feedService;

  @Test
  @DisplayName("피드 생성 API 성공")
  void createFeed_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        currentUserId,
        weatherId,
        List.of(clothesId),
        "오늘의 피드"
    );

    FeedDto response = mockFeedDto();

    given(feedService.createFeed(any(FeedCreateRequest.class), eq(currentUserId)))
        .willReturn(response);

    mockMvc.perform(post("/api/feeds")
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    verify(feedService).createFeed(any(FeedCreateRequest.class), eq(currentUserId));
  }

  @Test
  @DisplayName("피드 수정 API 성공")
  void updateFeed_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    FeedUpdateRequest request = new FeedUpdateRequest("수정된 피드 내용");

    FeedDto response = mockFeedDto();

    given(feedService.updateFeed(eq(feedId), any(FeedUpdateRequest.class), eq(currentUserId)))
        .willReturn(response);

    mockMvc.perform(patch("/api/feeds/{feedId}", feedId)
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(feedService).updateFeed(eq(feedId), any(FeedUpdateRequest.class), eq(currentUserId));
  }

  @Test
  @DisplayName("피드 삭제 API 성공")
  void deleteFeed_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    mockMvc.perform(delete("/api/feeds/{feedId}", feedId)
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(feedService).deleteFeed(feedId, currentUserId);
  }

  private Authentication mockAuthentication(UUID userId) {
    return new UsernamePasswordAuthenticationToken(userId, null, List.of());
  }

  private FeedDto mockFeedDto() {
    return new FeedDto(
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        List.of(),
        "피드 내용",
        0L,
        0,
        false
    );
  }

  @Test
  @DisplayName("피드 좋아요 생성 API 성공 테스트")
  void createFeedLike_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    mockMvc.perform(post("/api/feeds/{feedId}/like", feedId)
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(feedService).createFeedLike(feedId, currentUserId);
  }

  @Test
  @DisplayName("피드 좋아요 취소 API 성공 테스트")
  void deleteFeedLike_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    mockMvc.perform(delete("/api/feeds/{feedId}/like", feedId)
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(feedService).deleteFeedLike(feedId, currentUserId);
  }

  @Test
  @DisplayName("피드 댓글 생성 API 성공 테스트")
  void createFeedComment_success() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    FeedCommentCreateRequest request = new FeedCommentCreateRequest(
        feedId,
        currentUserId,
        "댓글 내용"
    );

    FeedCommentDto response = mock(FeedCommentDto.class);

    given(feedService.createFeedComment(
        eq(feedId),
        any(FeedCommentCreateRequest.class),
        eq(currentUserId)
    )).willReturn(response);

    mockMvc.perform(post("/api/feeds/{feedId}/comments", feedId)
            .with(authentication(mockAuthentication(currentUserId)))
            .with(csrf())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(feedService).createFeedComment(
        eq(feedId),
        any(FeedCommentCreateRequest.class),
        eq(currentUserId)
    );
  }
}