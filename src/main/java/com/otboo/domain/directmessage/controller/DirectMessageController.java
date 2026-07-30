package com.otboo.domain.directmessage.controller;

import com.otboo.domain.directmessage.dto.response.DirectMessageDtoCursorResponse;
import com.otboo.domain.directmessage.service.DirectMessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/direct-messages")
public class DirectMessageController {

  private final DirectMessageService directMessageService;

  @GetMapping
  public ResponseEntity<DirectMessageDtoCursorResponse> getDirectMessages(
      @RequestParam UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam @Min(1) @Max(100) int limit,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();

    DirectMessageDtoCursorResponse response = directMessageService.getDirectMessages(
        userId,
        cursor,
        idAfter,
        limit,
        currentUserId
    );

    return ResponseEntity.ok(response);
  }
}