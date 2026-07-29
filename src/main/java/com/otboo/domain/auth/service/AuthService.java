package com.otboo.domain.auth.service;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.exception.InvalidCredentialsException;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.auth.token.RefreshTokenService;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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

  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi1S6i8ftGKQPMFbMi1fFUsQTQ.Fjuu";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final RefreshTokenService refreshTokenService;

  @Transactional
  public SignInResult signIn(SignInRequest request) {
    String normalizedEmail = request.username().toLowerCase(Locale.ROOT);

    Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

    String passwordHashToCheck = userOptional
        .map(User::getPasswordHash)
        .orElse(DUMMY_PASSWORD_HASH);
    boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHashToCheck);

    boolean emailNotFound = userOptional.isEmpty();
    boolean passwordInvalid = !passwordMatches;
    boolean accountLocked = userOptional.map(User::isLocked).orElse(false);

    if (emailNotFound || passwordInvalid || accountLocked) {
      log.info("로그인 실패");
      throw new InvalidCredentialsException();
    }

    User user = userOptional.get();
    user.refreshSession();

    String accessToken = jwtProvider.createAccessToken(
        user.getId(), user.getRole().name(), user.getTokenVersion()
    );
    String refreshToken = refreshTokenService.issue(user.getId());

    return new SignInResult(new JwtDto(UserDto.from(user), accessToken), refreshToken);
  }

  @Transactional
  public JwtDto refresh(String refreshToken) {
    UUID userId = refreshTokenService.findUserId(refreshToken)
        .orElseThrow(InvalidCredentialsException::new);

    User user = userRepository.findById(userId)
        .orElseThrow(InvalidCredentialsException::new);

    if (user.isLocked()) {
      throw new InvalidCredentialsException();
    }

    String accessToken = jwtProvider.createAccessToken(
        user.getId(), user.getRole().name(), user.getTokenVersion()
    );

    return new JwtDto(UserDto.from(user), accessToken);
  }

  @Transactional
  public void signOut(String refreshToken) {
    if (refreshToken == null || !refreshTokenService.exists(refreshToken)) {
      throw new InvalidCredentialsException();
    }

    UUID userId = refreshTokenService.findUserId(refreshToken)
        .orElseThrow(InvalidCredentialsException::new);
    User user = userRepository.findById(userId)
        .orElseThrow(InvalidCredentialsException::new);

    user.refreshSession();
    refreshTokenService.delete(refreshToken);
  }

  public record SignInResult(JwtDto jwtDto, String refreshToken) {
  }
}