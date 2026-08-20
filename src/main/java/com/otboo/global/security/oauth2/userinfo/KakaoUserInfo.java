package com.otboo.global.security.oauth2.userinfo;

import java.util.Map;

public class KakaoUserInfo implements OAuth2UserInfo {

  private final Map<String, Object> attributes;

  public KakaoUserInfo(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  @Override
  public String getProviderId() {
    return String.valueOf(attributes.get("id"));
  }

  @Override
  public String getEmail() {
    return getKakaoAccount().get("email") != null
        ? (String) getKakaoAccount().get("email")
        : null;
  }

  @Override
  public String getName() {
    Map<String, Object> profile = getProfile();
    return profile != null ? (String) profile.get("nickname") : null;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getKakaoAccount() {
    Object kakaoAccount = attributes.get("kakao_account");
    return kakaoAccount != null ? (Map<String, Object>) kakaoAccount : Map.of();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getProfile() {
    Object profile = getKakaoAccount().get("profile");
    return profile != null ? (Map<String, Object>) profile : null;
  }
}