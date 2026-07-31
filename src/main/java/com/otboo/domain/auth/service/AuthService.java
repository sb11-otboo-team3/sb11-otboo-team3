package com.otboo.domain.auth.service;

import com.otboo.domain.auth.dto.JwtDto;
import com.otboo.domain.auth.dto.ResetPasswordRequest;
import com.otboo.domain.auth.dto.SignInRequest;
import com.otboo.domain.auth.exception.InvalidCredentialsException;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.auth.token.PasswordResetService;
import com.otboo.domain.auth.token.RefreshTokenService;
import com.otboo.domain.user.dto.ChangePasswordRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.otboo.domain.auth.dto.ResetPasswordRequest;
import com.otboo.domain.auth.token.PasswordResetService;

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
  private final PasswordResetService passwordResetService;

  @Transactional
  public SignInResult signIn(SignInRequest request) {
    String normalizedEmail = request.username().toLowerCase(Locale.ROOT);

    Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

    String passwordHashToCheck = userOptional
        .map(User::getPasswordHash)
        .orElse(DUMMY_PASSWORD_HASH);
    boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHashToCheck);

    boolean tempPasswordMatches = userOptional
        .flatMap(user -> passwordResetService.find(user.getId()))
        .map(tempPassword -> tempPassword.equals(request.password()))
        .orElse(false);

    boolean emailNotFound = userOptional.isEmpty();
    boolean passwordInvalid = !passwordMatches && !tempPasswordMatches;
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
    String refreshToken = refreshTokenService.issue(user.getId(), user.getTokenVersion());

    return new SignInResult(new JwtDto(UserDto.from(user), accessToken), refreshToken);
  }

  @Transactional
  public SignInResult refresh(String refreshToken) {
    if (refreshToken == null) {
      throw new InvalidCredentialsException();
    }

    RefreshTokenService.TokenInfo tokenInfo = refreshTokenService.consumeTokenInfo(refreshToken)
        .orElseThrow(InvalidCredentialsException::new);

    User user = userRepository.findById(tokenInfo.userId())
        .orElseThrow(InvalidCredentialsException::new);

    boolean tokenVersionMismatch = user.getTokenVersion() != tokenInfo.tokenVersion();
    if (user.isLocked() || tokenVersionMismatch) {
      throw new InvalidCredentialsException();
    }

    String newRefreshToken = refreshTokenService.issue(user.getId(), user.getTokenVersion());

    String accessToken = jwtProvider.createAccessToken(
        user.getId(), user.getRole().name(), user.getTokenVersion()
    );

    return new SignInResult(new JwtDto(UserDto.from(user), accessToken), newRefreshToken);
  }

  @Transactional
  public void signOut(String refreshToken) {
    if (refreshToken == null || !refreshTokenService.exists(refreshToken)) {
      throw new InvalidCredentialsException();
    }
    refreshTokenService.delete(refreshToken);
  }

  public record SignInResult(JwtDto jwtDto, String refreshToken) {
  }

  @Transactional(readOnly = true)
  public void resetPassword(ResetPasswordRequest request) {
    String normalizedEmail = request.email().toLowerCase(Locale.ROOT);
    User user = userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> new UserNotFoundException(normalizedEmail));
    passwordResetService.issue(user.getId());
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    String encodedPassword = passwordEncoder.encode(request.password());
    user.changePassword(encodedPassword);

    passwordResetService.delete(userId);
  }
}