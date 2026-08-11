package com.otboo.global.security;

import com.otboo.domain.auth.jwt.JwtAuthenticationFilter;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.repository.UserRepository;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
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
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            UserRepository userRepository,
            Environment environment
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
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll();

                        // local 프로필에서만 인증 없이 /actuator/prometheus 접근 허용 - 로컬 부하테스트 시
                        // Prometheus가 로그인 없이 긁어갈 수 있게 함
                        if (environment.matchesProfiles("local")) {
                            auth.requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll();
                        }

                        auth.anyRequest().authenticated();
                })
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(new CsrfTokenEagerLoadFilter(), CsrfFilter.class);

        return http.build();
    }
}