package com.otboo.global.security.oauth2.userinfo;

public interface OAuth2UserInfo {
  String getProviderId();
  String getEmail();
  String getName();
}