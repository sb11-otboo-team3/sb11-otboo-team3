package com.otboo.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.otboo.domain.user.entity.OAuthAccount;
import com.otboo.domain.user.entity.OAuthProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.OAuthAccountRepository;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.oauth2.userinfo.OAuth2UserInfo;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class CustomOAuth2UserServiceIntegrationTest {

  @Autowired
  private CustomOAuth2UserService customOAuth2UserService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private OAuthAccountRepository oAuthAccountRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @AfterEach
  void tearDown() {
    oAuthAccountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("이미 연동된 계정을 재조회할 때, 트랜잭션 밖에서 getRole()을 호출해도 LazyInitializationException이 발생하지 않는다")
  void findOrCreateUserInitializesProxyBeforeTransactionEnds() {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

    // given: User와 OAuthAccount를 하나의 트랜잭션 안에서 만들어 실제로 연동된 상태를 재현한다.
    txTemplate.execute(status -> {
      User user = User.create("relogin@otboo.io", "재로그인테스트", "encoded-password");
      userRepository.save(user);

      OAuthAccount oAuthAccount = OAuthAccount.create(user, OAuthProvider.GOOGLE, "google-relogin-id");
      oAuthAccountRepository.save(oAuthAccount);
      return null;
    });

    OAuth2UserInfo userInfo = fakeUserInfo("google-relogin-id", "relogin@otboo.io", "재로그인테스트");

    // when: findOrCreateUser는 자체 @Transactional을 갖고 있지 않으므로, 이 테스트
    // 메서드가 트랜잭션 밖에서 반환된 User에 접근하는 것과 같은 상황을 재현한다.
    User result = customOAuth2UserService.findOrCreateUser(OAuthProvider.GOOGLE, userInfo);

    // then: 트랜잭션이 끝난 뒤 getRole()을 호출해도 예외가 나지 않아야 한다
    // (수정 전이었다면 여기서 LazyInitializationException 발생)
    assertThatCode(result::getRole).doesNotThrowAnyException();
  }

  private OAuth2UserInfo fakeUserInfo(String providerId, String email, String name) {
    return new OAuth2UserInfo() {
      @Override
      public String getProviderId() {
        return providerId;
      }

      @Override
      public String getEmail() {
        return email;
      }

      @Override
      public String getName() {
        return name;
      }
    };
  }
}