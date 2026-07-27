package com.otboo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
    given(jwtProvider.createAccessToken(user.getId(), "USER", 0L)).willReturn("access-token");

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
}