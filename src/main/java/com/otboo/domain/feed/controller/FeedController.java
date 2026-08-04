package com.otboo.domain.feed.controller;

import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.service.FeedService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedController {

  private final FeedService feedService;

  @PostMapping
  public ResponseEntity<FeedDto> createFeed(
      @Valid @RequestBody FeedCreateRequest request,
      Authentication authentication
  ){
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FeedDto response = feedService.createFeed(request, currentUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/{feedId}")
  public ResponseEntity<FeedDto> updateFeed(
      @PathVariable UUID feedId,
      @Valid @RequestBody FeedUpdateRequest request,
      Authentication authentication
  ){
    UUID currentUserId = (UUID) authentication.getPrincipal();
    FeedDto response = feedService.updateFeed(feedId, request, currentUserId);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{feedId}")
  public ResponseEntity<Void> deleteFeed(
      @PathVariable UUID feedId,
      Authentication authentication
  ){
    UUID currentUserId = (UUID) authentication.getPrincipal();
    feedService.deleteFeed(feedId, currentUserId);
    return ResponseEntity.noContent().build();
  }
}
