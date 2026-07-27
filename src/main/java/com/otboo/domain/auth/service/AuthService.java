package com.otboo.domain.auth.service;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.exception.InvalidCredentialsException;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
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

  // 실존하지 않는 사용자에 대해서도 동일한 시간이 걸리도록 사용할 더미 해시.
  // 실제 사용자 비밀번호와 무관한 임의의 BCrypt 해시값이다.
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi1S6i8ftGKQPMFbMi1fFUsQTQ.Fjuu";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;

  public JwtDto signIn(SignInRequest request) {
    String normalizedEmail = request.username().toLowerCase();

    Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

    // 사용자 존재 여부와 무관하게 항상 동일하게 bcrypt 연산을 수행한다.
    String passwordHashToCheck = userOptional
        .map(User::getPasswordHash)
        .orElse(DUMMY_PASSWORD_HASH);
    boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHashToCheck);

    if (userOptional.isEmpty()) {
      log.info("로그인 실패 - 존재하지 않는 이메일: {}", normalizedEmail);
      throw new InvalidCredentialsException();
    }

    User user = userOptional.get();

    if (!passwordMatches) {
      log.info("로그인 실패 - 비밀번호 불일치: {}", normalizedEmail);
      throw new InvalidCredentialsException();
    }

    if (user.isLocked()) {
      log.info("로그인 실패 - 잠긴 계정: {}", normalizedEmail);
      throw new InvalidCredentialsException();
    }

    String accessToken = jwtProvider.createAccessToken(
        user.getId(), user.getRole().name(), user.getTokenVersion()
    );

    return new JwtDto(UserDto.from(user), accessToken);
  }
}