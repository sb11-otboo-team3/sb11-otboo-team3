package com.otboo.domain.follow.controller;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
