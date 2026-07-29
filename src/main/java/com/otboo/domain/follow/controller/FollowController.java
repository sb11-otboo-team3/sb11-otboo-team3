package com.otboo.domain.follow.controller;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

  private final FollowService followService;

  @PostMapping
  public ResponseEntity<FollowDto> createFollow(
      @Valid @RequestBody FollowCreateRequest request
  ) {
    FollowDto response = followService.createFollow(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> cancelFollow(@PathVariable UUID followId) {
    followService.cancelFollow(followId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/followings")
  public ResponseEntity<FollowListResponse> getFollowings(
      @RequestParam UUID followerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다.") int limit,
      @RequestParam(required = false) String nameLike
  ) {
    FollowListResponse response = followService.getFollowings(
        followerId, cursor, idAfter, limit, nameLike
    );

    return ResponseEntity.ok(response);
  }

  @GetMapping("/followers")
  public ResponseEntity<FollowListResponse> getFollowers(
      @RequestParam UUID followeeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다.") int limit,
      @RequestParam(required = false) String nameLike
  ){
    FollowListResponse response = followService.getFollowers(
        followeeId, cursor, idAfter, limit, nameLike
    );

    return ResponseEntity.ok(response);
  }
}
