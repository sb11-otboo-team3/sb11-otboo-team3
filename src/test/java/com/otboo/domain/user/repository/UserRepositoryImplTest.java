package com.otboo.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.InvalidUserCursorException;
import com.otboo.domain.user.exception.InvalidUserFilterException;
import com.otboo.global.config.JpaAuditingConfig;
import com.otboo.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class UserRepositoryImplTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("roleEqual에 잘못된 값을 전달하면 InvalidUserFilterException이 발생한다")
  void findUsersWithInvalidRoleEqualThrowsInvalidUserFilterException() {
    // given
    saveUser("filtertest@otboo.io", "필터테스트");

    // when & then
    assertThatThrownBy(() -> userRepository.findUsers(
        null, null, 10, "createdAt", "DESCENDING", null, "INVALID_ROLE", null
    )).isInstanceOf(InvalidUserFilterException.class);
  }

  @Test
  @DisplayName("roleEqual에 유효한 값을 전달하면 정상 조회된다")
  void findUsersWithValidRoleEqualReturnsFilteredResult() {
    // given
    User admin = saveUser("admintest@otboo.io", "어드민테스트");
    admin.changeRole(UserRole.ADMIN);
    userRepository.saveAndFlush(admin);

    saveUser("usertest@otboo.io", "유저테스트");

    // when
    List<User> result = userRepository.findUsers(
        null, null, 10, "createdAt", "DESCENDING", null, "ADMIN", null
    );

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getEmail()).isEqualTo("admintest@otboo.io");
  }

  private User saveUser(String email, String name) {
    User user = User.create(email, name, "encoded-password");
    return userRepository.saveAndFlush(user);
  }

  @Test
  @DisplayName("cursor가 잘못된 날짜 형식이면 InvalidUserCursorException이 발생한다")
  void findUsersWithInvalidCursorFormatThrowsInvalidUserCursorException() {
    // given
    saveUser("cursortest@otboo.io", "커서테스트");

    // when & then
    assertThatThrownBy(() -> userRepository.findUsers(
        "not-a-valid-instant", UUID.randomUUID(), 10, "createdAt", "DESCENDING", null, null, null
    )).isInstanceOf(InvalidUserCursorException.class);
  }
}