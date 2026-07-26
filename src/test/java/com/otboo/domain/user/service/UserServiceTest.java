package com.otboo.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private UserService userService;

  @Test
  void 회원가입에_성공하면_UserDto를_반환한다() {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userRepository.existsByEmail(request.email())).willReturn(false);
    given(passwordEncoder.encode(request.password())).willReturn("encoded-password");

    User savedUser = User.create(request.email(), request.name(), "encoded-password");
    given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

    // when
    UserDto result = userService.create(request);

    // then
    assertThat(result.email()).isEqualTo("test@otboo.io");
    assertThat(result.name()).isEqualTo("테스트유저");
    assertThat(result.role()).isEqualTo(UserRole.USER);
    assertThat(result.locked()).isFalse();
    verify(passwordEncoder).encode("password1234");
  }

  @Test
  void 이미_등록된_이메일이면_예외가_발생한다() {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userRepository.existsByEmail(request.email())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  void 사전검사_통과후_저장시점에_유니크제약_위반되면_DuplicateEmailException을_던진다() {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userRepository.existsByEmail(request.email())).willReturn(false);
    given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
    given(userRepository.saveAndFlush(any(User.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

    // when & then
    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(DuplicateEmailException.class);
  }

}