package com.otboo.domain.profile.controller;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @GetMapping("/{userId}/profiles")
  public ResponseEntity<ProfileDto> getProfile(
      @PathVariable UUID userId,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    ProfileDto response = profileService.getProfile(userId, currentUserId);
    return ResponseEntity.ok(response);
  }

  @PatchMapping(value = "/{userId}/profiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProfileDto> updateProfile(
      @PathVariable UUID userId,
      @Valid @RequestPart("request") ProfileUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    ProfileDto response = profileService.updateProfile(userId, currentUserId, request, image);
    return ResponseEntity.ok(response);
  }
}