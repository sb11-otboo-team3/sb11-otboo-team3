package com.otboo.global.security.oauth2.userinfo;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.user.entity.OAuthProvider;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuth2UserInfoFactoryTest {

  @Test
  @DisplayName("Google 응답에서 sub, email, name을 정확히 추출한다")
  void googleUserInfoExtractsAttributesCorrectly() {
    // given
    Map<String, Object> attributes = Map.of(
        "sub", "1234567890",
        "email", "test@gmail.com",
        "name", "홍길동"
    );

    // when
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.getOAuth2UserInfo(OAuthProvider.GOOGLE, attributes);

    // then
    assertThat(userInfo.getProviderId()).isEqualTo("1234567890");
    assertThat(userInfo.getEmail()).isEqualTo("test@gmail.com");
    assertThat(userInfo.getName()).isEqualTo("홍길동");
  }

  @Test
  @DisplayName("Kakao 응답의 중첩된 구조(kakao_account.profile)에서 email, name을 정확히 추출한다")
  void kakaoUserInfoExtractsNestedAttributesCorrectly() {
    // given
    Map<String, Object> attributes = Map.of(
        "id", 987654321,
        "kakao_account", Map.of(
            "email", "test@kakao.com",
            "profile", Map.of(
                "nickname", "홍길동"
            )
        )
    );

    // when
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.getOAuth2UserInfo(OAuthProvider.KAKAO, attributes);

    // then
    assertThat(userInfo.getProviderId()).isEqualTo("987654321");
    assertThat(userInfo.getEmail()).isEqualTo("test@kakao.com");
    assertThat(userInfo.getName()).isEqualTo("홍길동");
  }

  @Test
  @DisplayName("Kakao 응답에 kakao_account가 없으면 이메일과 이름은 null이다")
  void kakaoUserInfoReturnsNullWhenKakaoAccountMissing() {
    // given
    Map<String, Object> attributes = Map.of("id", 111111111);

    // when
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.getOAuth2UserInfo(OAuthProvider.KAKAO, attributes);

    // then
    assertThat(userInfo.getProviderId()).isEqualTo("111111111");
    assertThat(userInfo.getEmail()).isNull();
    assertThat(userInfo.getName()).isNull();
  }

  @Test
  @DisplayName("Kakao 응답에 이메일 동의가 없으면(kakao_account는 있으나 email 없음) 이메일은 null이다")
  void kakaoUserInfoReturnsNullEmailWhenNotConsented() {
    // given
    Map<String, Object> attributes = Map.of(
        "id", 222222222,
        "kakao_account", Map.of(
            "profile", Map.of("nickname", "홍길동")
        )
    );

    // when
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.getOAuth2UserInfo(OAuthProvider.KAKAO, attributes);

    // then
    assertThat(userInfo.getEmail()).isNull();
    assertThat(userInfo.getName()).isEqualTo("홍길동");
  }
}