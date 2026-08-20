package com.otboo.global.security.oauth2;

import com.otboo.domain.user.entity.OAuthAccount;
import com.otboo.domain.user.entity.OAuthProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.OAuthAccountRepository;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.oauth2.userinfo.OAuth2UserInfo;
import com.otboo.global.security.oauth2.userinfo.OAuth2UserInfoFactory;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;
  private final OAuthAccountRepository oAuthAccountRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    OAuthProvider provider = OAuthProvider.valueOf(registrationId.toUpperCase(Locale.ROOT));

    Map<String, Object> attributes = oAuth2User.getAttributes();
    OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(provider, attributes);

    User user = findOrCreateUser(provider, userInfo);

    return new CustomOAuth2User(user, attributes);
  }

  User findOrCreateUser(OAuthProvider provider, OAuth2UserInfo userInfo) {
    // 1. 이미 이 Provider로 연동된 계정이 있는지 먼저 확인한다.
    return oAuthAccountRepository
        .findByProviderAndProviderUserId(provider, userInfo.getProviderId())
        .map(OAuthAccount::getUser)
        .orElseGet(() -> linkOrCreateUser(provider, userInfo));
  }

  User linkOrCreateUser(OAuthProvider provider, OAuth2UserInfo userInfo) {
    String email = userInfo.getEmail();

    // 2. 이메일이 제공됐고, 이미 같은 이메일로 가입된 계정이 있으면 자동으로 연동한다.
    //    (Google/Kakao 모두 검증된 이메일만 제공하므로 신뢰할 수 있다고 판단)
    if (email != null && !email.isBlank()) {
      String normalizedEmail = email.toLowerCase(Locale.ROOT);
      User existingUser = userRepository.findByEmail(normalizedEmail).orElse(null);

      if (existingUser != null) {
        log.info(
            "기존 계정에 소셜 로그인을 연동합니다. email={}, provider={}",
            normalizedEmail, provider
        );
        linkOAuthAccount(existingUser, provider, userInfo.getProviderId());
        return existingUser;
      }
    }

    // 3. 이메일이 없거나(예: Kakao 이메일 미동의), 매칭되는 기존 계정이 없으면 신규 가입시킨다.
    return createNewUser(provider, userInfo);
  }

  User createNewUser(OAuthProvider provider, OAuth2UserInfo userInfo) {
    String email = resolveEmail(provider, userInfo);
    String name = userInfo.getName() != null ? userInfo.getName() : provider.name() + " 사용자";

    // 소셜 로그인 전용 계정은 비밀번호로 로그인하지 않으므로, 추측 불가능한
    // 무작위 값을 인코딩해 채워 넣는다 (User.passwordHash는 NOT NULL 제약).
    String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());

    User newUser = User.create(email, name, randomPassword);
    User savedUser = userRepository.save(newUser);

    log.info("소셜 로그인으로 신규 계정을 생성했습니다. email={}, provider={}", email, provider);

    linkOAuthAccount(savedUser, provider, userInfo.getProviderId());

    return savedUser;
  }

  private String resolveEmail(OAuthProvider provider, OAuth2UserInfo userInfo) {
    if (userInfo.getEmail() != null && !userInfo.getEmail().isBlank()) {
      return userInfo.getEmail().toLowerCase(Locale.ROOT);
    }
    // 이메일 제공에 동의하지 않은 경우(주로 Kakao)를 대비해 가상 이메일을 생성한다.
    return provider.name().toLowerCase(Locale.ROOT) + "_" + userInfo.getProviderId()
        + "@" + provider.name().toLowerCase(Locale.ROOT) + ".otboo.io";
  }

  private void linkOAuthAccount(User user, OAuthProvider provider, String providerUserId) {
    OAuthAccount oAuthAccount = OAuthAccount.create(user, provider, providerUserId);
    oAuthAccountRepository.save(oAuthAccount);
  }
}