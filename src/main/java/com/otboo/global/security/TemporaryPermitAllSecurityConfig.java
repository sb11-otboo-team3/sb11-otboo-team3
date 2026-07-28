package com.otboo.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// TODO: 인증/인가 기능 구현되면 이 클래스는 삭제할 것. 그 전까지 로컬/테스트에서
// 스프링 시큐리티 기본 계정(PasswordEncoderConfig와 충돌해 로그인 불가)을 우회하기 위한 임시 설정.
@Configuration
@Profile("!prod")
public class TemporaryPermitAllSecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}