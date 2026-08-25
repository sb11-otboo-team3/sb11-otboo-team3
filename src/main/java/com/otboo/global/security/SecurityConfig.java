package com.otboo.global.security;

import com.otboo.domain.auth.jwt.JwtAuthenticationFilter;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.logging.RequestIdMdcFilter;
import com.otboo.global.logging.UserIdMdcFilter;
import com.otboo.global.security.oauth2.CustomOAuth2UserService;
import com.otboo.global.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.otboo.global.security.oauth2.OAuth2LoginFailureHandler;
import com.otboo.global.security.oauth2.OAuth2LoginSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
    return new HttpCookieOAuth2AuthorizationRequestRepository();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      Environment environment,
      JwtProvider jwtProvider,
      UserRepository userRepository,
      ObjectProvider<CustomOAuth2UserService> customOAuth2UserServiceProvider,
      ObjectProvider<OAuth2LoginSuccessHandler> oAuth2LoginSuccessHandlerProvider,
      ObjectProvider<OAuth2LoginFailureHandler> oAuth2LoginFailureHandlerProvider,
      ObjectProvider<HttpCookieOAuth2AuthorizationRequestRepository> authorizationRequestRepositoryProvider
  ) throws Exception {
    // XSRF-TOKEN 쿠키는 프론트엔드 JS가 값을 읽어 X-XSRF-TOKEN 헤더에
    // 실어 보내야 하는 Double Submit Cookie 패턴이라,
    // 의도적으로 HttpOnly=false로 설정합니다.
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookieName("XSRF-TOKEN");
    csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");

    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfTokenRepository)
            .csrfTokenRequestHandler(requestHandler)
            .ignoringRequestMatchers("/ws/**")
        )
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint((request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value()))
            .accessDeniedHandler((request, response, accessDeniedException) ->
                response.sendError(HttpStatus.FORBIDDEN.value())))
        .authorizeHttpRequests(auth -> {
          auth
              // 프론트엔드 정적 리소스 접근 허용
              .requestMatchers(
                  "/",
                  "/index.html",
                  "/assets/**",
                  "/*.svg",
                  "/favicon.ico",
                  "/error"
              ).permitAll()

              .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
              .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
              .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
              .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
              .requestMatchers(HttpMethod.POST, "/api/auth/sign-out").permitAll()
              .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll()
              .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
              .requestMatchers("/ws/**").permitAll()
              .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
              .requestMatchers(
                  "/swagger-ui/**",
                  "/swagger-ui.html",
                  "/v3/api-docs/**"
              ).permitAll();

          // 로컬 프로메테우스(compose.yaml)가 인증 없이 스크래핑할 수 있게 로컬 프로필에서만 허용.
          // 운영은 계속 인증 필요(anyRequest().authenticated()로 막힘) - MSK/actuator 노출 범위는
          // 네트워크 계층(VPC 내부망)에서 별도로 관리한다.
          if (environment.matchesProfiles("local")) {
            auth.requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll();
          }

          auth.anyRequest().authenticated();
        })
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider, userRepository),
            UsernamePasswordAuthenticationFilter.class
        )
        .addFilterBefore(
            new RequestIdMdcFilter(),
            DisableEncodeUrlFilter.class
        )
        .addFilterAfter(
            new UserIdMdcFilter(),
            JwtAuthenticationFilter.class
        )
        .addFilterAfter(
            new CsrfTokenEagerLoadFilter(),
            AuthorizationFilter.class
        );

    // CustomOAuth2UserService 등은 @WebMvcTest 슬라이스 테스트에서는
    // Bean으로 등록되지 않으므로(웹 레이어 밖의 @Service이기 때문),
    // ObjectProvider로 선택적으로 주입받아 있을 때만 oauth2Login을 활성화한다.
    // 이렇게 하면 다른 도메인의 @WebMvcTest 컨트롤러 테스트들이 이 변경으로
    // 영향받지 않는다. (#165)
    CustomOAuth2UserService customOAuth2UserService =
        customOAuth2UserServiceProvider.getIfAvailable();

    if (customOAuth2UserService != null) {
      http.oauth2Login(oauth2 -> oauth2
          .authorizationEndpoint(endpoint -> endpoint
              .authorizationRequestRepository(
                  authorizationRequestRepositoryProvider.getObject()))
          .userInfoEndpoint(userInfo -> userInfo
              .userService(customOAuth2UserService))
          .successHandler(oAuth2LoginSuccessHandlerProvider.getObject())
          .failureHandler(oAuth2LoginFailureHandlerProvider.getObject())
      );
    }

    return http.build();
  }
}