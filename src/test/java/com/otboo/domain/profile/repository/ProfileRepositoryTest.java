package com.otboo.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProfileRepositoryTest {

  @Autowired
  private ProfileRepository profileRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("두 트랜잭션이 같은 프로필을 동시에 수정하면 낙관적 락 충돌이 발생한다")
  void concurrentUpdateThrowsOptimisticLockException() {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

    UUID userId = txTemplate.execute(status -> {
      User user = User.create("optimistictest@otboo.io", "낙관적락테스트", "encoded-password");
      userRepository.saveAndFlush(user);
      Profile profile = Profile.createDefault(user);
      profileRepository.saveAndFlush(profile);
      return user.getId();
    });

    entityManager.clear();

    // 서로 다른 트랜잭션 + 캐시 초기화로, 진짜 독립적인 두 객체를 만든다.
    Profile firstLoad = txTemplate.execute(status ->
        profileRepository.findById(userId).orElseThrow());

    entityManager.clear();

    Profile secondLoad = txTemplate.execute(status ->
        profileRepository.findById(userId).orElseThrow());

    entityManager.clear();

    // 첫 번째 트랜잭션에서 먼저 수정 및 커밋
    txTemplate.execute(status -> {
      firstLoad.updateImageKey("profiles/" + userId + "/first-writer.png");
      return profileRepository.saveAndFlush(firstLoad);
    });

    entityManager.clear();

    // 두 번째(오래된 version을 가진) 엔티티로 별도 트랜잭션에서 수정 시도
    secondLoad.updateImageKey("profiles/" + userId + "/second-writer.png");

    assertThatThrownBy(() ->
        txTemplate.execute(status -> profileRepository.saveAndFlush(secondLoad))
    ).isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }
}