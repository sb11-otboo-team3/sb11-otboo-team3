package com.otboo.domain.follow.controller;

import com.otboo.domain.follow.controller.docs.CancelFollowApi;
import com.otboo.domain.follow.controller.docs.CreateFollowApi;
import com.otboo.domain.follow.controller.docs.FollowApi;
import com.otboo.domain.follow.controller.docs.GetFollowSummaryApi;
import com.otboo.domain.follow.controller.docs.GetFollowersApi;
import com.otboo.domain.follow.controller.docs.GetFollowingsApi;
import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import com.otboo.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@FollowApi
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

  private final FollowService followService;

  @CreateFollowApi
  @PostMapping
  public ResponseEntity<FollowDto> createFollow(
      @Valid @RequestBody FollowCreateRequest request,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FollowDto response = followService.createFollow(request, currentUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @CancelFollowApi
  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> cancelFollow(
      @PathVariable UUID followId,
      Authentication authentication) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    followService.cancelFollow(followId, currentUserId);
    return ResponseEntity.noContent().build();
  }

  @GetFollowingsApi
  @GetMapping("/followings")
  public ResponseEntity<FollowListResponse> getFollowings(
      @RequestParam UUID followerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(value = 1, message = "limit는 1 이상이어야 합니다.") @Max(value = 100, message = "limit는 100 이하여야 합니다.") int limit,
      @RequestParam(required = false) String nameLike
  ) {
    FollowListResponse response = followService.getFollowings(
        followerId, cursor, idAfter, limit, nameLike
    );

    return ResponseEntity.ok(response);
  }

  @GetFollowersApi
  @GetMapping("/followers")
  public ResponseEntity<FollowListResponse> getFollowers(
      @RequestParam UUID followeeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다.") @Max(value = 100, message = "limit은 100 이하여야 합니다.") int limit,
      @RequestParam(required = false) String nameLike
  ) {
    FollowListResponse response = followService.getFollowers(
        followeeId, cursor, idAfter, limit, nameLike
    );

    return ResponseEntity.ok(response);
  }

  @GetFollowSummaryApi
  @GetMapping("/summary")
  public ResponseEntity<FollowSummaryDto> getFollowSummary
      (
          @RequestParam UUID userId,
          Authentication authentication
      ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FollowSummaryDto response = followService.getFollowSummary(userId, currentUserId);
    return ResponseEntity.ok(response);
  }
}
