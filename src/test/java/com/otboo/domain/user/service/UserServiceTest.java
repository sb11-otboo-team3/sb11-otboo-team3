package com.otboo.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.dto.UserLockUpdateRequest;
import com.otboo.domain.user.dto.UserRoleUpdateRequest;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.user.exception.UserNotFoundException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.dto.UserDtoCursorResponse;
import com.otboo.domain.user.exception.InvalidUserCursorException;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private UserService userService;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private EntityManager entityManager;

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
    verify(userRepository).incrementTokenVersion(userId);
    verify(entityManager).refresh(user);
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
    verify(userRepository).incrementTokenVersion(userId);
    verify(entityManager).refresh(user);
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
    verify(userRepository).incrementTokenVersion(userId);
    verify(entityManager).refresh(user);
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

  @Test
  @DisplayName("계정 목록을 조회하면 UserDtoCursorResponse를 반환한다")
  void getUsersReturnsUserDtoCursorResponse() throws Exception {
    // given
    User user1 = User.create("user1@otboo.io", "유저1", "encoded-password");
    User user2 = User.create("user2@otboo.io", "유저2", "encoded-password");
    ReflectionTestUtils.setField(user1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(user2, "id", UUID.randomUUID());

    given(userRepository.findUsers(
        eq(null), eq(null), eq(11), eq("createdAt"), eq("DESCENDING"),
        eq(null), eq(null), eq(null)
    )).willReturn(List.of(user1, user2));
    given(userRepository.countUsers(null, null, null)).willReturn(2L);

    // when
    UserDtoCursorResponse result = userService.getUsers(
        null, null, 10, "createdAt", "DESCENDING", null, null, null
    );

    // then
    assertThat(result.data()).hasSize(2);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("DESCENDING");
  }

  @Test
  @DisplayName("다음 페이지가 있으면 hasNext가 true이고 nextCursor를 반환한다")
  void getUsersWithNextPageReturnsHasNextTrue() throws Exception {
    // given
    User user1 = User.create("user1@otboo.io", "유저1", "encoded-password");
    User user2 = User.create("user2@otboo.io", "유저2", "encoded-password");
    UUID user2Id = UUID.randomUUID();
    ReflectionTestUtils.setField(user1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(user1, "createdAt", Instant.now());
    ReflectionTestUtils.setField(user2, "id", user2Id);
    ReflectionTestUtils.setField(user2, "createdAt", Instant.now());

    given(userRepository.findUsers(
        eq(null), eq(null), eq(2), eq("createdAt"), eq("DESCENDING"),
        eq(null), eq(null), eq(null)
    )).willReturn(List.of(user1, user2));
    given(userRepository.countUsers(null, null, null)).willReturn(5L);

    // when
    UserDtoCursorResponse result = userService.getUsers(
        null, null, 1, "createdAt", "DESCENDING", null, null, null
    );

    // then
    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(user1.getCreatedAt().toString());
    assertThat(result.nextIdAfter()).isEqualTo(user1.getId());
  }

  @Test
  @DisplayName("emailLike/roleEqual/locked 필터를 그대로 리포지토리에 전달한다")
  void getUsersPassesFiltersToRepository() throws Exception {
    // given
    given(userRepository.findUsers(
        eq(null), eq(null), eq(11), eq("createdAt"), eq("DESCENDING"),
        eq("test"), eq("ADMIN"), eq(true)
    )).willReturn(List.of());
    given(userRepository.countUsers("test", "ADMIN", true)).willReturn(0L);

    // when
    UserDtoCursorResponse result = userService.getUsers(
        null, null, 10, "createdAt", "DESCENDING", "test", "ADMIN", true
    );

    // then
    assertThat(result.data()).isEmpty();
    assertThat(result.totalCount()).isEqualTo(0L);
  }

  @Test
  @DisplayName("cursor만 있고 idAfter가 없으면 예외가 발생한다")
  void getUsersWithCursorOnlyThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> userService.getUsers(
        Instant.now().toString(), null, 10, "createdAt", "DESCENDING", null, null, null
    )).isInstanceOf(InvalidUserCursorException.class);
  }

  @Test
  @DisplayName("idAfter만 있고 cursor가 없으면 예외가 발생한다")
  void getUsersWithIdAfterOnlyThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> userService.getUsers(
        null, UUID.randomUUID(), 10, "createdAt", "DESCENDING", null, null, null
    )).isInstanceOf(InvalidUserCursorException.class);
  }

  @Test
  @DisplayName("createdAt 정렬에서 파싱 불가능한 cursor면 예외가 발생한다")
  void getUsersWithInvalidCreatedAtCursorThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> userService.getUsers(
        "not-a-valid-instant", UUID.randomUUID(), 10, "createdAt", "DESCENDING", null, null, null
    )).isInstanceOf(InvalidUserCursorException.class);
  }

  @Test
  @DisplayName("잘못된 sortBy 값이면 예외가 발생한다")
  void getUsersWithInvalidSortByThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> userService.getUsers(
        null, null, 10, "invalidField", "DESCENDING", null, null, null
    )).isInstanceOf(InvalidUserCursorException.class);
  }

  @Test
  @DisplayName("잘못된 sortDirection 값이면 예외가 발생한다")
  void getUsersWithInvalidSortDirectionThrowsException() throws Exception {
    // when & then
    assertThatThrownBy(() -> userService.getUsers(
        null, null, 10, "createdAt", "invalidDirection", null, null, null
    )).isInstanceOf(InvalidUserCursorException.class);
  }

  @Test
  @DisplayName("sortBy를 대문자로 보내도 정상 동작한다")
  void getUsersWithUpperCaseSortByWorksCorrectly() throws Exception {
    // given
    given(userRepository.findUsers(
        eq(null), eq(null), eq(11), eq("CREATEDAT"), eq("DESCENDING"),
        eq(null), eq(null), eq(null)
    )).willReturn(List.of());
    given(userRepository.countUsers(null, null, null)).willReturn(0L);

    // when
    UserDtoCursorResponse result = userService.getUsers(
        null, null, 10, "CREATEDAT", "DESCENDING", null, null, null
    );

    // then
    assertThat(result.data()).isEmpty();
  }

  @Test
  @DisplayName("동일한 권한으로 변경 요청하면 tokenVersion을 증가시키지 않는다")
  void changeRoleWithSameRoleDoesNotIncreaseTokenVersion() throws Exception {
    // given
    User user = User.create("samerole@otboo.io", "동일권한테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    // User는 기본 USER role로 생성됨

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.USER);

    // when
    userService.changeRole(userId, request);

    // then
    verify(userRepository, never()).incrementTokenVersion(any());
    verify(entityManager, never()).refresh(any());
  }

  @Test
  @DisplayName("이미 잠긴 계정을 다시 잠그도록 요청하면 tokenVersion을 증가시키지 않는다")
  void updateLockWithSameLockStateDoesNotIncreaseTokenVersion() throws Exception {
    // given
    User user = User.create("samelock@otboo.io", "동일잠금테스트", "encoded-password");
    user.lock();
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    UserLockUpdateRequest request = new UserLockUpdateRequest(true);

    // when
    userService.updateLock(userId, request);

    // then
    verify(userRepository, never()).incrementTokenVersion(any());
    verify(entityManager, never()).refresh(any());
  }
}