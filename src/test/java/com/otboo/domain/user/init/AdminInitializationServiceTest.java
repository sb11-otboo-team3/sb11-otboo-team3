package com.otboo.domain.user.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminInitializationServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private final AdminProperties adminProperties = new AdminProperties(
          "admin@otboo.io",
          "admin",
          "admin1234!"
  );

  @Test
  @DisplayName("설정된 초기 어드민 이메일이 없으면 초기 어드민 계정을 생성한다")
  void createsAdminWhenInitEmailDoesNotExist() {
    // given
    given(userRepository.existsByEmail("admin@otboo.io"))
            .willReturn(false);

    given(passwordEncoder.encode("admin1234!"))
            .willReturn("encoded-password");

    AdminInitializationService service =
            new AdminInitializationService(
                    userRepository,
                    passwordEncoder,
                    adminProperties
            );

    // when
    service.initializeAdmin();

    // then
    ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(User.class);

    verify(userRepository).saveAndFlush(captor.capture());

    User saved = captor.getValue();

    assertThat(saved.getEmail())
            .isEqualTo("admin@otboo.io");

    assertThat(saved.getRole())
            .isEqualTo(UserRole.ADMIN);
  }

  @Test
  @DisplayName("초기 어드민 이메일이 이미 등록되어 있으면 초기화를 건너뛴다")
  void skipsWhenInitEmailAlreadyExists() {
    // given
    given(userRepository.existsByEmail("admin@otboo.io"))
            .willReturn(true);

    AdminInitializationService service =
            new AdminInitializationService(
                    userRepository,
                    passwordEncoder,
                    adminProperties
            );

    // when
    service.initializeAdmin();

    // then
    verify(passwordEncoder, never()).encode(any());
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("다른 인스턴스가 동시에 초기 어드민을 생성한 경우 정상적으로 건너뛴다")
  void skipsWhenAnotherInstanceCreatesAdminConcurrently() {
    // given
    User concurrentAdmin = User.create(
            "admin@otboo.io",
            "admin",
            "encoded-password"
    );

    concurrentAdmin.changeRole(UserRole.ADMIN);

    given(userRepository.existsByEmail("admin@otboo.io"))
            .willReturn(false);

    given(passwordEncoder.encode("admin1234!"))
            .willReturn("encoded-password");

    given(userRepository.saveAndFlush(any()))
            .willThrow(
                    new DataIntegrityViolationException(
                            "duplicate key"
                    )
            );

    given(userRepository.findByEmail("admin@otboo.io"))
            .willReturn(Optional.of(concurrentAdmin));

    AdminInitializationService service =
            new AdminInitializationService(
                    userRepository,
                    passwordEncoder,
                    adminProperties
            );

    // when & then
    assertThatCode(service::initializeAdmin)
            .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("동시 어드민 생성으로 설명되지 않는 DB 제약조건 오류는 다시 발생시킨다")
  void propagatesUnexpectedDataIntegrityViolationException() {
    // given
    DataIntegrityViolationException exception =
            new DataIntegrityViolationException(
                    "unexpected constraint violation"
            );

    given(userRepository.existsByEmail("admin@otboo.io"))
            .willReturn(false);

    given(passwordEncoder.encode("admin1234!"))
            .willReturn("encoded-password");

    given(userRepository.saveAndFlush(any()))
            .willThrow(exception);

    given(userRepository.findByEmail("admin@otboo.io"))
            .willReturn(Optional.empty());

    AdminInitializationService service =
            new AdminInitializationService(
                    userRepository,
                    passwordEncoder,
                    adminProperties
            );

    // when & then
    assertThatThrownBy(service::initializeAdmin)
            .isSameAs(exception);
  }
}