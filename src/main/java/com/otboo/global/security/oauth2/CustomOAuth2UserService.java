package com.otboo.global.security.oauth2;

import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
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
  private final ProfileRepository profileRepository;
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

  @Transactional
  User findOrCreateUser(OAuthProvider provider, OAuth2UserInfo userInfo) {
    // 이미 이 Provider로 연동된 계정이 있는지 먼저 확인한다.
    return oAuthAccountRepository
        .findByProviderAndProviderUserId(provider, userInfo.getProviderId())
        .map(this::initializeUser)
        .orElseGet(() -> createNewUser(provider, userInfo));
  }

  private User initializeUser(OAuthAccount oAuthAccount) {
    // OAuthAccount.user는 지연 로딩(LAZY) 프록시라, 이 트랜잭션이 끝난 뒤
    // (Spring Security가 CustomOAuth2User.getAuthorities()를 호출하는 시점)에
    // 접근하면 LazyInitializationException이 발생한다. 트랜잭션이 살아있는
    // 지금 시점에 필요한 필드(role)를 미리 읽어와 프록시를 초기화해둔다. (#213)
    User user = oAuthAccount.getUser();
    user.getRole();
    return user;
  }

  User createNewUser(OAuthProvider provider, OAuth2UserInfo userInfo) {
    // 기존에는 이메일이 같으면 기존 계정에 자동으로 연동했으나, 사용자가
    // 명시적으로 동의하지 않은 계정 통합은 혼란을 줄 수 있다는 판단에 따라
    // 제거. 이메일이 같은 기존 계정이 있어도 항상 별도의 소셜 전용 계정을
    // 새로 생성한다. (#219)
    String email = resolveEmail(provider, userInfo);
    String name = userInfo.getName() != null ? userInfo.getName() : provider.name() + " 사용자";

    // 소셜 로그인 전용 계정은 비밀번호로 로그인하지 않으므로, 추측 불가능한
    // 무작위 값을 인코딩해 채워 넣는다 (User.passwordHash는 NOT NULL 제약).
    String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());

    User newUser = User.create(email, name, randomPassword);
    User savedUser = userRepository.save(newUser);

    // 일반 회원가입(UserService.create())과 동일하게, 신규 계정 생성 시
    // 기본 프로필도 함께 만들어야 한다. 누락 시 이후 프로필 조회/수정
    // 요청이 전부 ProfileNotFoundException으로 실패한다. (#213)
    Profile profile = Profile.createDefault(savedUser);
    profileRepository.save(profile);

    log.info("소셜 로그인으로 신규 계정을 생성했습니다. email={}, provider={}", email, provider);

    linkOAuthAccount(savedUser, provider, userInfo.getProviderId());

    return savedUser;
  }

  private String resolveEmail(OAuthProvider provider, OAuth2UserInfo userInfo) {
    // 항상 Provider별 가상 이메일을 사용한다. 실제 이메일을 그대로 쓰면
    // 이미 같은 이메일로 가입한 계정이 있을 때 User.email UNIQUE 제약을
    // 위반할 수 있고, 계정 완전 분리 정책(#219)과도 맞지 않는다.
    return provider.name().toLowerCase(Locale.ROOT) + "_" + userInfo.getProviderId()
        + "@" + provider.name().toLowerCase(Locale.ROOT) + ".otboo.io";
  }

  private void linkOAuthAccount(User user, OAuthProvider provider, String providerUserId) {
    OAuthAccount oAuthAccount = OAuthAccount.create(user, provider, providerUserId);
    oAuthAccountRepository.save(oAuthAccount);
  }
}