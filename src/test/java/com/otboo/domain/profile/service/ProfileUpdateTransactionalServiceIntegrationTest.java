package com.otboo.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.infrastructure.storage.FileStorage;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ProfileUpdateTransactionalServiceIntegrationTest {

  @Autowired
  private ProfileUpdateTransactionalService profileUpdateTransactionalService;

  @Autowired
  private ProfileRepository profileRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @MockitoBean
  private FileStorage fileStorage;

  @AfterEach
  void tearDown() {
    profileRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("이미지 교체 트랜잭션이 커밋되면 AFTER_COMMIT 리스너가 실제로 기존 이미지 삭제를 시도한다")
  void afterCommitListenerDeletesOldImageOnRealCommit() {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

    // given: user와 profile 저장을 하나의 트랜잭션 안에서 처리해,
    // Profile이 참조하는 User가 detached 상태가 되지 않도록 한다.
    UUID userId = txTemplate.execute(status -> {
      User user = User.create("txintegration@otboo.io", "통합테스트", "encoded-password");
      userRepository.save(user);

      Profile profile = Profile.createDefault(user);
      profile.updateImageKey("profiles/" + user.getId() + "/old-key.png");
      profileRepository.save(profile);

      return user.getId();
    });

    ProfileUpdateRequest request = new ProfileUpdateRequest(null, null, null, null, null);
    String newImageKey = "profiles/" + userId + "/new-key.png";

    // when: 실제 @Transactional 프록시를 거쳐 커밋까지 완료된다.
    ProfileDto result = profileUpdateTransactionalService.update(
        userId, request, null, newImageKey, "https://example.com/" + newImageKey
    );

    // then
    assertThat(result.profileImageUrl()).contains(newImageKey);

    // DB에 실제로 새 이미지 키가 저장됐는지 다시 조회해서 확인한다.
    // (profile.updateImageKey(newImageKey) 호출이 누락되어도 응답 DTO만으로는
    // 이를 탐지할 수 없으므로, 영속화된 상태를 직접 검증한다.)
    Profile updatedProfile = profileRepository.findById(userId).orElseThrow();
    assertThat(updatedProfile.getImageKey()).isEqualTo(newImageKey);

    // @TransactionalEventListener(AFTER_COMMIT)는 별도 @Async 처리가 없어
    // 트랜잭션 커밋 직후 같은 스레드에서 동기적으로 실행된다.
    verify(fileStorage).delete("profiles/" + userId + "/old-key.png");
  }
}