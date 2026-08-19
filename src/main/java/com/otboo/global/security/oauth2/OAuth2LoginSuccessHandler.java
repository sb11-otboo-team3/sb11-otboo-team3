package com.otboo.global.security.oauth2;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.auth.token.RefreshTokenService;
import com.otboo.domain.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    // Refresh Token은 일반 로그인과 동일하게 HttpOnly 쿠키로 내려준다.
    Cookie refreshTokenCookie = new Cookie("REFRESH_TOKEN", refreshToken);
    refreshTokenCookie.setHttpOnly(true);
    refreshTokenCookie.setSecure(request.isSecure());
    refreshTokenCookie.setPath("/");
    response.addCookie(refreshTokenCookie);

    String targetUrl = redirectUri + "?accessToken="
        + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}