package com.otboo.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

  private final HttpCookieOAuth2AuthorizationRequestRepository repository =
      new HttpCookieOAuth2AuthorizationRequestRepository();

  @Test
  @DisplayName("인증 요청을 저장하면 쿠키로 다시 조회할 수 있다")
  void saveAndLoadAuthorizationRequestRoundTrips() {
    // given
    OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest
        .authorizationCode()
        .authorizationUri("https://accounts.google.com/o/oauth2/auth")
        .clientId("test-client-id")
        .redirectUri("https://otboo.work/login/oauth2/code/google")
        .state("test-state")
        .authorizationRequestUri("https://accounts.google.com/o/oauth2/auth?client_id=test-client-id")
        .build();

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when: 저장
    repository.saveAuthorizationRequest(authorizationRequest, request, response);

    // then: 응답에 담긴 쿠키를 다음 요청에 그대로 실어서 재조회
    MockHttpServletRequest nextRequest = new MockHttpServletRequest();
    nextRequest.setCookies(response.getCookies());

    OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(nextRequest);

    assertThat(loaded).isNotNull();
    assertThat(loaded.getState()).isEqualTo("test-state");
    assertThat(loaded.getClientId()).isEqualTo("test-client-id");
  }

  @Test
  @DisplayName("쿠키가 없으면 null을 반환한다")
  void loadAuthorizationRequestReturnsNullWhenCookieMissing() {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();

    // when
    OAuth2AuthorizationRequest result = repository.loadAuthorizationRequest(request);

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("removeAuthorizationRequest 호출 시 쿠키를 만료시킨다")
  void removeAuthorizationRequestExpiresCookie() {
    // given
    OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest
        .authorizationCode()
        .authorizationUri("https://kauth.kakao.com/oauth/authorize")
        .clientId("test-client-id")
        .redirectUri("https://otboo.work/login/oauth2/code/kakao")
        .state("remove-state")
        .authorizationRequestUri("https://kauth.kakao.com/oauth/authorize?client_id=test-client-id")
        .build();

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    repository.saveAuthorizationRequest(authorizationRequest, request, response);

    MockHttpServletRequest removeRequest = new MockHttpServletRequest();
    removeRequest.setCookies(response.getCookies());
    MockHttpServletResponse removeResponse = new MockHttpServletResponse();

    // when
    OAuth2AuthorizationRequest removed =
        repository.removeAuthorizationRequest(removeRequest, removeResponse);

    // then
    assertThat(removed).isNotNull();
    assertThat(removeResponse.getCookie(
        HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
    ).getMaxAge()).isZero();
  }
}