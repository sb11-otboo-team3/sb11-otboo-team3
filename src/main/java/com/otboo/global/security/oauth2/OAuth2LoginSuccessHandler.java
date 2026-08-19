package com.otboo.global.security.oauth2;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.auth.token.RefreshTokenService;
import com.otboo.domain.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtProvider jwtProvider;
  private final RefreshTokenService refreshTokenService;

  @Value("${app.oauth2.redirect-uri}")
  private String redirectUri;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication
  ) throws IOException, ServletException {
    CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
    User user = oAuth2User.getUser();

    String accessToken = jwtProvider.createAccessToken(
        user.getId(), user.getRole().name(), user.getTokenVersion()
    );
    String refreshToken = refreshTokenService.issue(user.getId(), user.getTokenVersion());

    // AccessToken, RefreshToken 둘 다 HttpOnly 쿠키로 내려준다.
    // URL 파라미터로 토큰을 노출하지 않기 위함이며, 프론트가 별도로
    // 토큰을 받아 저장하는 로직 없이도 이후 요청에 자동으로 실려간다.
    setTokenCookie(response, "ACCESS_TOKEN", accessToken, request.isSecure());
    setTokenCookie(response, "REFRESH_TOKEN", refreshToken, request.isSecure());

    getRedirectStrategy().sendRedirect(request, response, redirectUri);
  }

  private void setTokenCookie(HttpServletResponse response, String name, String value, boolean secure) {
    Cookie cookie = new Cookie(name, value);
    cookie.setHttpOnly(true);
    cookie.setSecure(secure);
    cookie.setPath("/");
    response.addCookie(cookie);
  }
}