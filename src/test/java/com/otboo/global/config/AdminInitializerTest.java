package com.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private final AdminProperties adminProperties = new AdminProperties(
      "admin@otboo.io", "admin", "admin1234!"
  );

  @Test
  @DisplayName("ADMIN 계정이 없으면 초기 어드민 계정을 생성한다")
  void createsAdminWhenNoneExists() throws Exception {
    // given
    given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false);
    given(userRepository.existsByEmail("admin@otboo.io")).willReturn(false);
    given(passwordEncoder.encode("admin1234!")).willReturn("encoded-password");

    AdminInitializer initializer =
        new AdminInitializer(userRepository, passwordEncoder, adminProperties);

    // when
    initializer.run();

    // then
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());

    User saved = captor.getValue();
    assertThat(saved.getEmail()).isEqualTo("admin@otboo.io");
    assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
  }

  @Test
  @DisplayName("이미 ADMIN 계정이 존재하면 초기화를 건너뛴다")
  void skipsWhenAdminAlreadyExists() throws Exception {
    // given
    given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(true);

    AdminInitializer initializer =
        new AdminInitializer(userRepository, passwordEncoder, adminProperties);

    // when
    initializer.run();

    // then
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("초기 이메일이 이미 다른 계정으로 등록되어 있으면 초기화를 건너뛴다")
  void skipsWhenInitEmailAlreadyTaken() throws Exception {
    // given
    given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false);
    given(userRepository.existsByEmail("admin@otboo.io")).willReturn(true);

    AdminInitializer initializer =
        new AdminInitializer(userRepository, passwordEncoder, adminProperties);

    // when
    initializer.run();

    // then
    verify(userRepository, never()).save(any());
  }
}