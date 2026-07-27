package com.otboo.domain.auth.service;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.exception.InvalidCredentialsException;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;

  public JwtDto signIn(SignInRequest request) {
    String normalizedEmail = request.username().toLowerCase();

    User user = userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> {
          log.info("로그인 실패 - 존재하지 않는 이메일: {}", normalizedEmail);
          return new InvalidCredentialsException();
        });

    if (user.isLocked()) {
      log.info("로그인 실패 - 잠긴 계정: {}", normalizedEmail);
      throw new InvalidCredentialsException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      log.info("로그인 실패 - 비밀번호 불일치: {}", normalizedEmail);
      throw new InvalidCredentialsException();
    }

    String accessToken = jwtProvider.createAccessToken(
        user.getId(), user.getRole().name(), user.getTokenVersion()
    );

    return new JwtDto(UserDto.from(user), accessToken);
  }
}