package com.otboo.domain.auth.controller;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/sign-in")
  public ResponseEntity<JwtDto> signIn(@ModelAttribute SignInRequest request) {
    JwtDto response = authService.signIn(request);
    return ResponseEntity.ok(response);
  }
}