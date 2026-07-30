package com.otboo.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.dto.UserLockUpdateRequest;
import com.otboo.domain.user.dto.UserRoleUpdateRequest;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private UserService userService;

  @Test
  @DisplayName("회원가입에 성공하면 UserDto를 반환한다")
  void signUpSuccessReturnsUserDto() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");

    given(userRepository.existsByEmail("test@otboo.io")).willReturn(false);
    given(passwordEncoder.encode("password1234")).willReturn("encoded-password");

    User savedUser = User.create("test@otboo.io", "테스트유저", "encoded-password");
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
  @DisplayName("이미 등록된 이메일이면 예외가 발생한다")
  void signUpWithDuplicateEmailThrowsException() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userRepository.existsByEmail("test@otboo.io")).willReturn(true);

    // when & then
    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @DisplayName("사전검사 통과 후 저장 시점에 유니크 제약 위반되면 DuplicateEmailException을 던진다")
  void signUpWithRaceConditionThrowsDuplicateEmailException() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userRepository.existsByEmail("test@otboo.io")).willReturn(false);
    given(passwordEncoder.encode("password1234")).willReturn("encoded-password");
    given(userRepository.saveAndFlush(any(User.class)))
        .willThrow(new DataIntegrityViolationException(
            "could not execute statement; SQL [n/a]; constraint [uk6dotkott2kjsp8vw4d0m25fb7]"));

    // when & then
    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @DisplayName("이메일이 아닌 다른 제약 위반이면 DuplicateEmailException을 던지지 않는다")
  void signUpWithOtherConstraintViolationDoesNotThrowDuplicateEmailException() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "test@otboo.io", "password1234");
    given(userRepository.existsByEmail(any())).willReturn(false);
    given(passwordEncoder.encode("password1234")).willReturn("encoded-password");
    given(userRepository.saveAndFlush(any(User.class)))
        .willThrow(new DataIntegrityViolationException("some other constraint violation"));

    // when & then
    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @DisplayName("대소문자만 다른 이메일은 중복으로 처리된다")
  void signUpWithDifferentCaseEmailIsTreatedAsDuplicate() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest("테스트유저", "Test@otboo.io", "password1234");
    given(userRepository.existsByEmail("test@otboo.io")).willReturn(true);

    // when & then
    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @DisplayName("권한을 변경하면 UserDto를 반환한다")
  void changeRoleReturnsUpdatedUserDto() throws Exception {
    // given
    User user = User.create("roletest@otboo.io", "권한테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.ADMIN);

    // when
    UserDto result = userService.changeRole(userId, request);

    // then
    assertThat(result.role()).isEqualTo(UserRole.ADMIN);
  }

  @Test
  @DisplayName("존재하지 않는 사용자의 권한을 변경하려 하면 예외가 발생한다")
  void changeRoleWithNonExistentUserThrowsException() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.ADMIN);

    // when & then
    assertThatThrownBy(() -> userService.changeRole(userId, request))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("계정을 잠그면 locked가 true인 UserDto를 반환한다")
  void updateLockLocksAccount() throws Exception {
    // given
    User user = User.create("locktest@otboo.io", "잠금테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    UserLockUpdateRequest request = new UserLockUpdateRequest(true);

    // when
    UserDto result = userService.updateLock(userId, request);

    // then
    assertThat(result.locked()).isTrue();
  }

  @Test
  @DisplayName("계정 잠금을 해제하면 locked가 false인 UserDto를 반환한다")
  void updateLockUnlocksAccount() throws Exception {
    // given
    User user = User.create("unlocktest@otboo.io", "잠금해제테스트", "encoded-password");
    user.lock();
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    UserLockUpdateRequest request = new UserLockUpdateRequest(false);

    // when
    UserDto result = userService.updateLock(userId, request);

    // then
    assertThat(result.locked()).isFalse();
  }

  @Test
  @DisplayName("존재하지 않는 사용자의 잠금 상태를 변경하려 하면 예외가 발생한다")
  void updateLockWithNonExistentUserThrowsException() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    UserLockUpdateRequest request = new UserLockUpdateRequest(true);

    // when & then
    assertThatThrownBy(() -> userService.updateLock(userId, request))
        .isInstanceOf(UserNotFoundException.class);
  }
}