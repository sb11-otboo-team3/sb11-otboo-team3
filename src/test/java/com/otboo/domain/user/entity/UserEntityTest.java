package com.otboo.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserEntityTest {

  @Autowired
  private EntityManager entityManager;

  @Test
  void User_저장시_id와_생성수정시각이_자동으로_등록된다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");

    // when
    entityManager.persist(user);
    entityManager.flush();

    // then
    assertThat(user.getId()).isNotNull();
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.isLocked()).isFalse();
    assertThat(user.getTokenVersion()).isZero();
  }

  @Test
  void 권한을_변경하면_role이_바뀐다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");

    // when
    user.changeRole(UserRole.ADMIN);

    // then
    assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
  }

  @Test
  void 계정을_잠그면_locked가_true가된다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");

    // when
    user.lock();

    // then
    assertThat(user.isLocked()).isTrue();
  }

  @Test
  void 계정_잠금을_해제하면_locked가_false가된다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    user.lock();

    // when
    user.unlock();

    // then
    assertThat(user.isLocked()).isFalse();
  }

  @Test
  void 비밀번호를_변경하면_passwordHash가_바뀐다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");

    // when
    user.changePassword("new-encoded-password");

    // then
    assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
  }

  @Test
  void 이메일은_소문자로_정규화되어_저장된다() {
    // given & when
    User user = User.create("Test@Otboo.io", "테스트유저", "encoded-password");

    // then
    assertThat(user.getEmail()).isEqualTo("test@otboo.io");
  }
}