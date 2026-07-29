package com.otboo.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.service.AuthService;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.global.security.SecurityConfig;
import org.springframework.context.annotation.Import;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @DisplayName("로그인 요청이 유효하면 200과 JwtDto를 반환한다")
  void signInSuccessReturns200() throws Exception {
    // given
    UserDto userDto = new UserDto(
        UUID.randomUUID(), Instant.now(), "test@otboo.io", "테스트유저", UserRole.USER, false
    );
    JwtDto jwtDto = new JwtDto(userDto, "access-token");
    AuthService.SignInResult result = new AuthService.SignInResult(jwtDto, "refresh-token-value");
    given(authService.signIn(any(SignInRequest.class))).willReturn(result);

    // when & then
    mockMvc.perform(multipart("/api/auth/sign-in")
            .param("username", "test@otboo.io")
            .param("password", "password1234")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("username이 비어있으면 400을 반환한다")
  void signInWithBlankUsernameReturns400() throws Exception {
    // when & then
    mockMvc.perform(multipart("/api/auth/sign-in")
            .param("username", "")
            .param("password", "password1234")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("password가 비어있으면 400을 반환한다")
  void signInWithBlankPasswordReturns400() throws Exception {
    // when & then
    mockMvc.perform(multipart("/api/auth/sign-in")
            .param("username", "test@otboo.io")
            .param("password", "")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CSRF 토큰 조회 시 204를 반환한다")
  void csrfTokenReturns204() throws Exception {
    mockMvc.perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("유효한 Refresh Token 쿠키로 재발급하면 200을 반환한다")
  void refreshWithValidCookieReturns200() throws Exception {
    // given
    UserDto userDto = new UserDto(
        UUID.randomUUID(), Instant.now(), "test@otboo.io", "테스트유저", UserRole.USER, false
    );
    JwtDto response = new JwtDto(userDto, "new-access-token");
    given(authService.refresh("valid-refresh-token")).willReturn(response);

    // when & then
    mockMvc.perform(post("/api/auth/refresh")
            .cookie(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", "valid-refresh-token"))
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Refresh Token 쿠키 없이 재발급 요청하면 400을 반환한다")
  void refreshWithoutCookieReturns400() throws Exception {
    mockMvc.perform(post("/api/auth/refresh")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("로그아웃 요청이 성공하면 204를 반환한다")
  void signOutReturns204() throws Exception {
    mockMvc.perform(post("/api/auth/sign-out")
            .cookie(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", "some-token"))
            .with(csrf()))
        .andExpect(status().isNoContent());
  }
}