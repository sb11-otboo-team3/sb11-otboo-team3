package com.otboo.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.service.AuthService;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService authService;

  @Test
  @WithMockUser
  @DisplayName("로그인 요청이 유효하면 200과 JwtDto를 반환한다")
  void signInSuccessReturns200() throws Exception {
    // given
    UserDto userDto = new UserDto(
        UUID.randomUUID(), Instant.now(), "test@otboo.io", "테스트유저", UserRole.USER, false
    );
    JwtDto response = new JwtDto(userDto, "access-token");
    given(authService.signIn(any(SignInRequest.class))).willReturn(response);

    // when & then
    mockMvc.perform(multipart("/api/auth/sign-in")
            .param("username", "test@otboo.io")
            .param("password", "password1234")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
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
  @WithMockUser
  @DisplayName("password가 비어있으면 400을 반환한다")
  void signInWithBlankPasswordReturns400() throws Exception {
    // when & then
    mockMvc.perform(multipart("/api/auth/sign-in")
            .param("username", "test@otboo.io")
            .param("password", "")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }
}