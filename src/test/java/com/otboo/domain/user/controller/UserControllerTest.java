package com.otboo.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.dto.UserLockUpdateRequest;
import com.otboo.domain.user.dto.UserRoleUpdateRequest;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.user.service.UserService;
import com.otboo.global.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.otboo.domain.auth.service.AuthService;
import com.otboo.domain.user.dto.UserDtoCursorResponse;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private AuthService authService;

  @Test
  @DisplayName("회원가입 요청이 유효하면 201을 반환한다")
  void signUpSuccessReturns201() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    UserDto response = new UserDto(
        UUID.randomUUID(), Instant.now(), "test@otboo.io", "테스트유저", UserRole.USER, false
    );
    given(userService.create(any(UserCreateRequest.class))).willReturn(response);

    // when & then
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test@otboo.io"))
        .andExpect(jsonPath("$.name").value("테스트유저"));
  }

  @Test
  @DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
  void signUpWithInvalidEmailFormatReturns400() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "invalid-email", "password1234");

    // when & then
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("이미 등록된 이메일이면 400을 반환한다")
  void signUpWithDuplicateEmailReturns400() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userService.create(any(UserCreateRequest.class)))
        .willThrow(new DuplicateEmailException(request.email()));

    // when & then
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("권한 변경 요청이 성공하면 200을 반환한다")
  void changeRoleReturns200() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UserDto response = new UserDto(
        userId, Instant.now(), "test@otboo.io", "테스트유저", UserRole.ADMIN, false
    );
    given(userService.changeRole(any(UUID.class), any(UserRoleUpdateRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                                {"role":"ADMIN"}
                                """)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("존재하지 않는 사용자의 권한을 변경하면 404를 반환한다")
  void changeRoleWithNonExistentUserReturns404() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(userService.changeRole(any(UUID.class), any(UserRoleUpdateRequest.class)))
        .willThrow(new UserNotFoundException(userId));

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                                {"role":"ADMIN"}
                                """)
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("계정 잠금 요청이 성공하면 200을 반환한다")
  void updateLockReturns200() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UserDto response = new UserDto(
        userId, Instant.now(), "test@otboo.io", "테스트유저", UserRole.USER, true
    );
    given(userService.updateLock(any(UUID.class), any(UserLockUpdateRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/lock", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                                {"locked":true}
                                """)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locked").value(true));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("존재하지 않는 사용자의 잠금 상태를 변경하면 404를 반환한다")
  void updateLockWithNonExistentUserReturns404() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(userService.updateLock(any(UUID.class), any(UserLockUpdateRequest.class)))
        .willThrow(new UserNotFoundException(userId));

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/lock", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                                {"locked":true}
                                """)
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("잘못된 role 값으로 요청하면 400을 반환한다")
  void changeRoleWithInvalidRoleValueReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"role":"MANAGER"}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("role 값 없이 요청하면 400을 반환한다")
  void changeRoleWithNullRoleReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("locked 값 없이 요청하면 400을 반환한다")
  void updateLockWithNullLockedReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/lock", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("USER 권한으로 권한 변경을 시도하면 403을 반환한다")
  void changeRoleWithUserRoleReturns403() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"role":"ADMIN"}
                            """)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("USER 권한으로 계정 잠금을 시도하면 403을 반환한다")
  void updateLockWithUserRoleReturns403() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/lock", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"locked":true}
                            """)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  @DisplayName("비밀번호 변경 요청이 성공하면 204를 반환한다")
  void changePasswordReturns204() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"newPassword1234"}
                            """)
            .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser
  @DisplayName("존재하지 않는 사용자의 비밀번호를 변경하면 404를 반환한다")
  void changePasswordWithNonExistentUserReturns404() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    org.mockito.BDDMockito.willThrow(new com.otboo.domain.user.exception.UserNotFoundException(userId))
        .given(authService).changePassword(any(), any());

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"newPassword1234"}
                            """)
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("6자 미만 비밀번호로 변경을 요청하면 400을 반환한다")
  void changePasswordWithTooShortPasswordReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .with(authentication(authentication))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"abc12"}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("영문자가 없는 비밀번호로 변경을 요청하면 400을 반환한다")
  void changePasswordWithoutLetterReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .with(authentication(authentication))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"123456"}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("숫자가 없는 비밀번호로 변경을 요청하면 400을 반환한다")
  void changePasswordWithoutDigitReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .with(authentication(authentication))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"abcdef"}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("허용되지 않은 특수문자가 포함된 비밀번호로 변경을 요청하면 400을 반환한다")
  void changePasswordWithDisallowedSpecialCharacterReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .with(authentication(authentication))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"abc123#"}
                            """)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("유효한 비밀번호로 변경을 요청하면 204를 반환한다")
  void changePasswordWithValidPasswordReturns204() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

    // when & then
    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .with(authentication(authentication))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"password":"abc123!"}
                            """)
            .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("ADMIN 권한으로 계정 목록을 조회하면 200을 반환한다")
  void getUsersReturns200() throws Exception {
    // given
    UserDto userDto = new UserDto(
        UUID.randomUUID(), Instant.now(), "test@otboo.io", "테스트유저", UserRole.USER, false
    );
    UserDtoCursorResponse response = new UserDtoCursorResponse(
        List.of(userDto), null, null, false, 1L, "createdAt", "DESCENDING"
    );
    given(userService.getUsers(any(), any(), any(Integer.class), any(), any(), any(), any(), any()))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/users")
            .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].email").value("test@otboo.io"))
        .andExpect(jsonPath("$.totalCount").value(1));
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("USER 권한으로 계정 목록을 조회하면 403을 반환한다")
  void getUsersWithUserRoleReturns403() throws Exception {
    // when & then
    mockMvc.perform(get("/api/users")
            .param("limit", "10"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("limit이 1 미만이면 400을 반환한다")
  void getUsersWithLimitBelowMinimumReturns400() throws Exception {
    // when & then
    mockMvc.perform(get("/api/users")
            .param("limit", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("limit이 100을 초과하면 400을 반환한다")
  void getUsersWithLimitAboveMaximumReturns400() throws Exception {
    // when & then
    mockMvc.perform(get("/api/users")
            .param("limit", "101"))
        .andExpect(status().isBadRequest());
  }
}