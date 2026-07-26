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
  void 권한_변경시_tokenVersion이_증가한다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");

    // when
    user.changeRole(UserRole.ADMIN);

    // then
    assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(user.getTokenVersion()).isEqualTo(1L);
  }
  @Test
  void 같은_권한으로_변경하면_tokenVersion이_증가하지_않는다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");

    // when
    user.changeRole(UserRole.USER);

    // then
    assertThat(user.getTokenVersion()).isZero();
  }

  @Test
  void 이미_잠긴_계정을_다시_잠그면_tokenVersion이_증가하지_않는다() {
    // given
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    user.lock();
    long versionAfterFirstLock = user.getTokenVersion();

    // when
    user.lock();

    // then
    assertThat(user.getTokenVersion()).isEqualTo(versionAfterFirstLock);
  }

}