package com.otboo.domain.user.controller;

import com.otboo.domain.auth.service.AuthService;
import com.otboo.domain.user.dto.ChangePasswordRequest;
import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.otboo.domain.user.dto.UserLockUpdateRequest;
import com.otboo.domain.user.dto.UserRoleUpdateRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import com.otboo.domain.user.dto.UserDtoCursorResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final AuthService authService;

  @PostMapping
  public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest request) {
    UserDto response = userService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserDtoCursorResponse> getUsers(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam
      @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
      @Max(value = 100, message = "limit는 100 이하여야 합니다.")
      int limit,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "DESCENDING") String sortDirection,
      @RequestParam(required = false) String emailLike,
      @RequestParam(required = false) String roleEqual,
      @RequestParam(required = false) Boolean locked
  ) {
    UserDtoCursorResponse response = userService.getUsers(
        cursor, idAfter, limit, sortBy, sortDirection, emailLike, roleEqual, locked
    );
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserDto> changeRole(
      @PathVariable UUID userId,
      @Valid @RequestBody UserRoleUpdateRequest request
  ) {
    UserDto response = userService.changeRole(userId, request);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/lock")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserDto> updateLock(
      @PathVariable UUID userId,
      @Valid @RequestBody UserLockUpdateRequest request
  ) {
    UserDto response = userService.updateLock(userId, request);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/password")
  public ResponseEntity<Void> changePassword(
      @PathVariable UUID userId,
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    authService.changePassword(userId, request);
    return ResponseEntity.noContent().build();
  }
}