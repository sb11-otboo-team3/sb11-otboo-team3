package com.otboo.global.security;

import com.otboo.domain.auth.jwt.JwtAuthenticationFilter;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtProvider jwtProvider,
      UserRepository userRepository
  ) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint((request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value())))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/users",
                "/api/auth/sign-in",
                "/actuator/health",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider, userRepository),
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
  }
}