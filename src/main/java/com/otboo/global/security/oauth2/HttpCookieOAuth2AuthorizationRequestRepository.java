package com.otboo.global.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;
import org.springframework.web.util.WebUtils;

// STATELESS 세션 정책을 유지하기 위해, OAuth2 인증 중간 상태(state 등)를
// HttpSession 대신 쿠키에 저장한다. (#165)
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME =
      "oauth2_auth_request";
  private static final int COOKIE_EXPIRE_SECONDS = 180;

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    return deserialize(cookie);
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    if (authorizationRequest == null) {
      removeAuthorizationRequestCookie(request, response);
      return;
    }

    Cookie cookie = new Cookie(
        OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
        serialize(authorizationRequest)
    );
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(request.isSecure());
    cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
    response.addCookie(cookie);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    removeAuthorizationRequestCookie(request, response);
    return authorizationRequest;
  }

  private void removeAuthorizationRequestCookie(
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    Cookie cookie = new Cookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
      objectStream.writeObject(authorizationRequest);
      objectStream.flush();
      return Base64.getUrlEncoder().encodeToString(byteStream.toByteArray());
    } catch (Exception e) {
      throw new IllegalStateException("OAuth2 인증 요청 직렬화에 실패했습니다.", e);
    }
  }

  private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
    if (cookie == null) {
      return null;
    }
    try (ObjectInputStream objectStream = new ObjectInputStream(
        new ByteArrayInputStream(Base64.getUrlDecoder().decode(cookie.getValue())))) {
      return (OAuth2AuthorizationRequest) objectStream.readObject();
    } catch (Exception e) {
      return null;
    }
  }
}