package com.otboo.domain.feed.controller;

import com.otboo.domain.feed.controller.docs.CreateFeedApi;
import com.otboo.domain.feed.controller.docs.CreateFeedCommentApi;
import com.otboo.domain.feed.controller.docs.CreateFeedLikeApi;
import com.otboo.domain.feed.controller.docs.DeleteFeedApi;
import com.otboo.domain.feed.controller.docs.DeleteFeedLikeApi;
import com.otboo.domain.feed.controller.docs.FeedApi;
import com.otboo.domain.feed.controller.docs.UpdateFeedApi;
import com.otboo.domain.feed.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.SortBy;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.request.SortDirection;
import com.otboo.domain.feed.dto.response.FeedCommentDto;
import com.otboo.domain.feed.dto.response.FeedCommentDtoCursorResponse;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.dto.response.FeedDtoCursorResponse;
import com.otboo.domain.feed.service.FeedService;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@FeedApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedController {

  private final FeedService feedService;

  @CreateFeedApi
  @PostMapping
  public ResponseEntity<FeedDto> createFeed(
      @Valid @RequestBody FeedCreateRequest request,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FeedDto response = feedService.createFeed(request, currentUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @UpdateFeedApi
  @PatchMapping("/{feedId}")
  public ResponseEntity<FeedDto> updateFeed(
      @PathVariable UUID feedId,
      @Valid @RequestBody FeedUpdateRequest request,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FeedDto response = feedService.updateFeed(feedId, request, currentUserId);
    return ResponseEntity.ok(response);
  }

  @DeleteFeedApi
  @DeleteMapping("/{feedId}")
  public ResponseEntity<Void> deleteFeed(
      @PathVariable UUID feedId,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    feedService.deleteFeed(feedId, currentUserId);
    return ResponseEntity.noContent().build();
  }

  @CreateFeedLikeApi
  @PostMapping("/{feedId}/like")
  public ResponseEntity<Void> createFeedLike(
      @PathVariable UUID feedId,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    feedService.createFeedLike(feedId, currentUserId);
    return ResponseEntity.noContent().build();
  }

  @DeleteFeedLikeApi
  @DeleteMapping("/{feedId}/like")
  public ResponseEntity<Void> deleteFeedLike(
      @PathVariable UUID feedId,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    feedService.deleteFeedLike(feedId, currentUserId);
    return ResponseEntity.noContent().build();
  }

  @CreateFeedCommentApi
  @PostMapping("/{feedId}/comments")
  public ResponseEntity<FeedCommentDto> createComment(
      @PathVariable UUID feedId,
      @Valid @RequestBody FeedCommentCreateRequest request,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FeedCommentDto response = feedService.createFeedComment(
        feedId, request, currentUserId
    );
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<FeedDtoCursorResponse> getFeeds(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(value = 1, message = "limit는 1 이상이어야 합니다.") @Max(value = 100, message = "limit는 100 이하여야 합니다.") int limit,
      @RequestParam SortBy sortBy,
      @RequestParam SortDirection sortDirection,
      @RequestParam(required = false) String keywordLike,
      @RequestParam(required = false) SkyStatus skyStatusEqual,
      @RequestParam(required = false) PrecipitationType precipitationTypeEqual,
      @RequestParam(required = false) UUID authorIdEqual,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FeedDtoCursorResponse response = feedService.getFeeds(
        cursor, idAfter, limit, sortBy, sortDirection, keywordLike, skyStatusEqual,
        precipitationTypeEqual, authorIdEqual, currentUserId
    );
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{feedId}/comments")
  public ResponseEntity<FeedCommentDtoCursorResponse> getComment(
      @PathVariable UUID feedId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(value = 1, message = "limit는 1 이상이어야 합니다.") @Max(value = 100, message = "limit는 100 이하여야 합니다.") int limit
  ) {
    FeedCommentDtoCursorResponse response = feedService.getComment(
        feedId, cursor, idAfter, limit
    );

    return ResponseEntity.ok(response);
  }
}
