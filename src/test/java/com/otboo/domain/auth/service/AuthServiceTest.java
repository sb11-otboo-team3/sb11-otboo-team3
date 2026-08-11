package com.otboo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.ArgumentMatchers.eq;

import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.exception.InvalidCredentialsException;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.auth.token.PasswordResetService;
import com.otboo.domain.auth.token.RefreshTokenService;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.otboo.domain.auth.dto.ResetPasswordRequest;
import com.otboo.domain.user.dto.ChangePasswordRequest;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.auth.token.RefreshTokenService.TokenInfo;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private RefreshTokenService refreshTokenService;

  @Mock
  private PasswordResetService passwordResetService;

  @InjectMocks
  private AuthService authService;

  @Mock
  private EntityManager entityManager;

  @Test
  @DisplayName("로그인에 성공하면 JwtDto와 refreshToken을 반환한다")
  void signInSuccessReturnsJwtDtoAndRefreshToken() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    SignInRequest request = new SignInRequest("test@otboo.io", "password1234");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password1234", "encoded-password")).willReturn(true);
    given(jwtProvider.createAccessToken(any(), any(), anyLong())).willReturn("access-token");
    given(refreshTokenService.issue(any(), anyLong())).willReturn("refresh-token-value");

    // when
    AuthService.SignInResult result = authService.signIn(request);

    // then
    assertThat(result.jwtDto().accessToken()).isEqualTo("access-token");
    assertThat(result.jwtDto().userDto().email()).isEqualTo("test@otboo.io");
    assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인하면 예외가 발생한다")
  void signInWithNonExistentEmailThrowsException() throws Exception {
    // given
    SignInRequest request = new SignInRequest("notfound@otboo.io", "password1234");
    given(userRepository.findByEmail("notfound@otboo.io")).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 예외가 발생한다")
  void signInWithWrongPasswordThrowsException() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    SignInRequest request = new SignInRequest("test@otboo.io", "wrong-password");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);
    given(passwordResetService.find(any())).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("잠긴 계정으로 로그인하면 예외가 발생한다")
  void signInWithLockedAccountThrowsException() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    user.lock();
    SignInRequest request = new SignInRequest("test@otboo.io", "password1234");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(passwordResetService.find(any())).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("로그인에 성공하면 tokenVersion을 원자적으로 증가시키고 최신 값을 다시 조회한다")
  void signInIncreasesTokenVersion() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    long versionBeforeLogin = user.getTokenVersion();

    SignInRequest request = new SignInRequest("test@otboo.io", "password1234");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password1234", "encoded-password")).willReturn(true);

    // entityManager.refresh(user) 호출 시, 실제 DB 원자적 증가를 흉내내어
    // 메모리상 tokenVersion도 +1 시킨다.
    willAnswer(invocation -> {
      ReflectionTestUtils.setField(user, "tokenVersion", versionBeforeLogin + 1);
      return null;
    }).given(entityManager).refresh(user);

    given(jwtProvider.createAccessToken(any(), any(), eq(versionBeforeLogin + 1)))
        .willReturn("access-token");
    given(refreshTokenService.issue(any(), eq(versionBeforeLogin + 1)))
        .willReturn("refresh-token-value");

    // when
    authService.signIn(request);

    // then
    verify(userRepository).incrementTokenVersion(userId);
    verify(entityManager).refresh(user);
    verify(jwtProvider).createAccessToken(any(), any(), eq(versionBeforeLogin + 1));
    verify(refreshTokenService).issue(any(), eq(versionBeforeLogin + 1));
  }

  @Test
  @DisplayName("유효한 Refresh Token으로 재발급하면 새 Access Token과 새 Refresh Token을 반환한다")
  void refreshWithValidTokenReturnsNewTokens() throws Exception {
    // given
    User user = User.create("refreshtest@otboo.io", "재발급테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    RefreshTokenService.TokenInfo tokenInfo =
        new RefreshTokenService.TokenInfo(userId, user.getTokenVersion());

    given(refreshTokenService.consumeTokenInfo("valid-refresh-token"))
        .willReturn(Optional.of(tokenInfo));
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(jwtProvider.createAccessToken(any(), any(), anyLong())).willReturn("new-access-token");
    given(refreshTokenService.issue(any(), anyLong())).willReturn("new-refresh-token");

    // when
    AuthService.SignInResult result = authService.refresh("valid-refresh-token");

    // then
    assertThat(result.jwtDto().accessToken()).isEqualTo("new-access-token");
    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
  }

  @Test
  @DisplayName("Refresh Token이 null이면 재발급 시 예외가 발생한다")
  void refreshWithNullTokenThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> authService.refresh(null))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("존재하지 않는 Refresh Token으로 재발급하면 예외가 발생한다")
  void refreshWithInvalidTokenThrowsException() throws Exception {
    // given
    given(refreshTokenService.consumeTokenInfo("invalid-token")).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.refresh("invalid-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("잠긴 계정의 Refresh Token으로 재발급하면 예외가 발생한다")
  void refreshWithLockedAccountThrowsException() throws Exception {
    // given
    User user = User.create("lockedrefresh@otboo.io", "잠긴계정테스트", "encoded-password");
    user.lock();
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    RefreshTokenService.TokenInfo tokenInfo =
        new RefreshTokenService.TokenInfo(userId, user.getTokenVersion());

    given(refreshTokenService.consumeTokenInfo("some-token")).willReturn(Optional.of(tokenInfo));
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // when & then
    assertThatThrownBy(() -> authService.refresh("some-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("tokenVersion이 일치하지 않는 Refresh Token으로 재발급하면 예외가 발생한다")
  void refreshWithMismatchedTokenVersionThrowsException() throws Exception {
    // given
    User user = User.create("versiontest@otboo.io", "버전테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    // Refresh Token엔 예전 버전(0)이 저장되어 있는데, User는 이미 버전이 올라간 상태
    RefreshTokenService.TokenInfo tokenInfo = new RefreshTokenService.TokenInfo(userId, 0L);
    user.changeRole(com.otboo.domain.user.entity.UserRole.ADMIN); // tokenVersion을 1로 올림

    given(refreshTokenService.consumeTokenInfo("stale-token")).willReturn(Optional.of(tokenInfo));
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // when & then
    assertThatThrownBy(() -> authService.refresh("stale-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("유효한 Refresh Token으로 로그아웃하면 원자적으로 소비된다")
  void signOutConsumesRefreshToken() throws Exception {
    // given
    given(refreshTokenService.consumeTokenInfo("some-refresh-token"))
        .willReturn(Optional.of(new TokenInfo(UUID.randomUUID(), 1L)));

    // when
    authService.signOut("some-refresh-token");

    // then
    verify(refreshTokenService).consumeTokenInfo("some-refresh-token");
  }

  @Test
  @DisplayName("Refresh Token이 null이면 예외가 발생한다")
  void signOutWithNullTokenThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> authService.signOut(null))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("존재하지 않거나 이미 소비된 Refresh Token으로 로그아웃하면 예외가 발생한다")
  void signOutWithNonExistentTokenThrowsException() throws Exception {
    // given
    given(refreshTokenService.consumeTokenInfo("invalid-token"))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.signOut("invalid-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("존재하는 이메일로 비밀번호를 초기화하면 임시비밀번호가 발급된다")
  void resetPasswordIssuesTemporaryPassword() throws Exception {
    // given
    User user = User.create("resettest@otboo.io", "초기화테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    given(userRepository.findByEmail("resettest@otboo.io")).willReturn(Optional.of(user));

    ResetPasswordRequest request = new ResetPasswordRequest("resettest@otboo.io");

    // when
    authService.resetPassword(request);

    // then
    verify(passwordResetService).issue(userId);
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 비밀번호를 초기화하면 예외가 발생한다")
  void resetPasswordWithNonExistentEmailThrowsException() throws Exception {
    // given
    given(userRepository.findByEmail("notfound@otboo.io")).willReturn(Optional.empty());

    ResetPasswordRequest request = new ResetPasswordRequest("notfound@otboo.io");

    // when & then
    assertThatThrownBy(() -> authService.resetPassword(request))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("임시비밀번호로 로그인하면 성공한다")
  void signInWithTemporaryPasswordSucceeds() throws Exception {
    // given
    User user = User.create("temptest@otboo.io", "임시비번테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    SignInRequest request = new SignInRequest("temptest@otboo.io", "temporary1!!");

    given(userRepository.findByEmail("temptest@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("temporary1!!", "encoded-password")).willReturn(false);
    given(passwordResetService.find(userId)).willReturn(Optional.of("temporary1!!"));
    given(jwtProvider.createAccessToken(any(), any(), anyLong())).willReturn("access-token");
    given(refreshTokenService.issue(any(), anyLong())).willReturn("refresh-token-value");

    // when
    AuthService.SignInResult result = authService.signIn(request);

    // then
    assertThat(result.jwtDto().accessToken()).isEqualTo("access-token");
  }

  @Test
  @DisplayName("임시비밀번호가 없는 상태에서 잘못된 비밀번호로 로그인하면 예외가 발생한다")
  void signInWithWrongPasswordAndNoTempPasswordThrowsException() throws Exception {
    // given
    User user = User.create("notemp@otboo.io", "임시비번없음테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    SignInRequest request = new SignInRequest("notemp@otboo.io", "wrong-password");

    given(userRepository.findByEmail("notemp@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);
    given(passwordResetService.find(userId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("비밀번호를 변경하면 새 비밀번호로 저장되고 임시비밀번호가 파기되며 tokenVersion이 증가한다")
  void changePasswordUpdatesPasswordAndDeletesTempPassword() throws Exception {
    // given
    User user = User.create("changetest@otboo.io", "변경테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    long versionBeforeChange = user.getTokenVersion();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(passwordEncoder.encode("newPassword1234")).willReturn("new-encoded-password");

    ChangePasswordRequest request = new ChangePasswordRequest("newPassword1234");

    // when
    authService.changePassword(userId, request);

    // then
    assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
    assertThat(user.getTokenVersion()).isEqualTo(versionBeforeChange + 1);
    verify(passwordResetService).delete(userId);
  }

  @Test
  @DisplayName("존재하지 않는 사용자의 비밀번호를 변경하려 하면 예외가 발생한다")
  void changePasswordWithNonExistentUserThrowsException() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    ChangePasswordRequest request = new ChangePasswordRequest("newPassword1234");

    // when & then
    assertThatThrownBy(() -> authService.changePassword(userId, request))
        .isInstanceOf(UserNotFoundException.class);
  }
}