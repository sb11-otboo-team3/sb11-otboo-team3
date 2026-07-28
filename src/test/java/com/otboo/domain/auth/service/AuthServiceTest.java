package com.otboo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.exception.InvalidCredentialsException;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtProvider jwtProvider;

  @InjectMocks
  private AuthService authService;

  @Test
  @DisplayName("로그인에 성공하면 JwtDto를 반환한다")
  void signInSuccessReturnsJwtDto() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    SignInRequest request = new SignInRequest("test@otboo.io", "password1234");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password1234", "encoded-password")).willReturn(true);
    given(jwtProvider.createAccessToken(any(), any(), anyLong())).willReturn("access-token");

    // when
    JwtDto result = authService.signIn(request);

    // then
    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.userDto().email()).isEqualTo("test@otboo.io");
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

    // when & then
    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("존재하지 않는 이메일과 존재하는 이메일의 비밀번호 검증 소요 시간 차이가 크지 않다")
  void signInTimingIsConsistentRegardlessOfEmailExistence() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    SignInRequest existingEmailRequest = new SignInRequest("test@otboo.io", "wrong-password");
    SignInRequest nonExistingEmailRequest = new SignInRequest("notfound@otboo.io", "wrong-password");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(userRepository.findByEmail("notfound@otboo.io")).willReturn(Optional.empty());
    given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

    // when & then
    // 두 경우 모두 passwordEncoder.matches가 호출되어야 함 (타이밍 통일 검증)
    assertThatThrownBy(() -> authService.signIn(existingEmailRequest))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThatThrownBy(() -> authService.signIn(nonExistingEmailRequest))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordEncoder, times(2)).matches(anyString(), anyString());
  }

  @Test
  @DisplayName("로그인에 성공하면 tokenVersion이 증가한다")
  void signInIncreasesTokenVersion() throws Exception {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    long versionBeforeLogin = user.getTokenVersion();
    SignInRequest request = new SignInRequest("test@otboo.io", "password1234");

    given(userRepository.findByEmail("test@otboo.io")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password1234", "encoded-password")).willReturn(true);
    given(jwtProvider.createAccessToken(any(), any(), anyLong())).willReturn("access-token");

    // when
    authService.signIn(request);

    // then
    assertThat(user.getTokenVersion()).isEqualTo(versionBeforeLogin + 1);
  }
}