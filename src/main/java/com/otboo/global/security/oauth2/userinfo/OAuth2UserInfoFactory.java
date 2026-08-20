package com.otboo.global.security.oauth2.userinfo;

import com.otboo.domain.user.entity.OAuthProvider;
import java.util.Map;

public class OAuth2UserInfoFactory {

  private OAuth2UserInfoFactory() {
  }

  public static OAuth2UserInfo getOAuth2UserInfo(
      OAuthProvider provider,
      Map<String, Object> attributes
  ) {
    return switch (provider) {
      case GOOGLE -> new GoogleUserInfo(attributes);
      case KAKAO -> new KakaoUserInfo(attributes);
    };
  }
}