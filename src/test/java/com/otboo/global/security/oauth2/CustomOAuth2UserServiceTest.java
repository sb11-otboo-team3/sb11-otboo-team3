package com.otboo.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.user.entity.OAuthAccount;
import com.otboo.domain.user.entity.OAuthProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.OAuthAccountRepository;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.oauth2.userinfo.OAuth2UserInfo;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private OAuthAccountRepository oAuthAccountRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private CustomOAuth2UserService customOAuth2UserService;

  @Test
  @DisplayName("이미 이 Provider로 연동된 계정이 있으면 기존 계정을 그대로 반환한다")
  void findOrCreateUserReturnsExistingLinkedUser() {
    // given
    User existingUser = User.create("linked@otboo.io", "연동된유저", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(existingUser, "id", userId);

    OAuthAccount oAuthAccount = OAuthAccount.create(existingUser, OAuthProvider.GOOGLE, "google-id-1");

    OAuth2UserInfo userInfo = fakeUserInfo("google-id-1", "linked@otboo.io", "연동된유저");

    given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-id-1"))
        .willReturn(Optional.of(oAuthAccount));

    // when
    User result = customOAuth2UserService.findOrCreateUser(OAuthProvider.GOOGLE, userInfo);

    // then
    assertThat(result).isEqualTo(existingUser);
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("연동 이력은 없지만 이메일이 같은 기존 계정이 있으면 자동으로 연동한다")
  void linkOrCreateUserLinksToExistingAccountByEmail() {
    // given
    User existingUser = User.create("same@otboo.io", "기존유저", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(existingUser, "id", userId);

    OAuth2UserInfo userInfo = fakeUserInfo("google-id-2", "same@otboo.io", "새이름");

    given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-id-2"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail("same@otboo.io")).willReturn(Optional.of(existingUser));

    // when
    User result = customOAuth2UserService.findOrCreateUser(OAuthProvider.GOOGLE, userInfo);

    // then
    assertThat(result).isEqualTo(existingUser);
    verify(oAuthAccountRepository).save(any(OAuthAccount.class));
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("일치하는 기존 계정이 없으면 신규 계정을 생성한다")
  void createNewUserWhenNoMatchingAccountExists() {
    // given
    OAuth2UserInfo userInfo = fakeUserInfo("google-id-3", "brand-new@otboo.io", "새유저");

    given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-id-3"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail("brand-new@otboo.io")).willReturn(Optional.empty());
    given(passwordEncoder.encode(any())).willReturn("encoded-random-password");
    given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

    // when
    User result = customOAuth2UserService.findOrCreateUser(OAuthProvider.GOOGLE, userInfo);

    // then
    assertThat(result.getEmail()).isEqualTo("brand-new@otboo.io");
    verify(userRepository).save(any(User.class));
    verify(oAuthAccountRepository).save(any(OAuthAccount.class));
  }

  @Test
  @DisplayName("이메일이 없으면(Kakao 미동의) 가상 이메일로 신규 계정을 생성한다")
  void createNewUserWithVirtualEmailWhenEmailIsNull() {
    // given
    OAuth2UserInfo userInfo = fakeUserInfo("kakao-id-1", null, "카카오유저");

    given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "kakao-id-1"))
        .willReturn(Optional.empty());
    given(passwordEncoder.encode(any())).willReturn("encoded-random-password");
    given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

    // when
    User result = customOAuth2UserService.findOrCreateUser(OAuthProvider.KAKAO, userInfo);

    // then
    assertThat(result.getEmail()).contains("kakao-id-1").contains("kakao.otboo.io");
    verify(userRepository, never()).findByEmail(any());
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