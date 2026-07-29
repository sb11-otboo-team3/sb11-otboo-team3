package com.otboo.domain.auth.controller;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";
  private static final int REFRESH_TOKEN_MAX_AGE = 60 * 60 * 24 * 7; // 7일(초)

  private final AuthService authService;

  @PostMapping("/sign-in")
  public ResponseEntity<JwtDto> signIn(
      @Valid @ModelAttribute SignInRequest request,
      HttpServletResponse response
  ) {
    AuthService.SignInResult result = authService.signIn(request);
    addRefreshTokenCookie(response, result.refreshToken());
    return ResponseEntity.ok(result.jwtDto());
  }

  @PostMapping("/refresh")
  public ResponseEntity<JwtDto> refresh(
      @CookieValue(REFRESH_TOKEN_COOKIE) String refreshToken
  ) {
    JwtDto response = authService.refresh(refreshToken);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/sign-out")
  public ResponseEntity<Void> signOut(
      @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
      HttpServletResponse response
  ) {
    authService.signOut(refreshToken);
    clearRefreshTokenCookie(response);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/csrf-token")
  public ResponseEntity<Void> csrfToken() {
    return ResponseEntity.noContent().build();
  }

  private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
    response.addCookie(cookie);
  }

  private void clearRefreshTokenCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, null);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }
}