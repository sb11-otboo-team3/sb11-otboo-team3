package com.otboo.domain.user.service;

import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.otboo.domain.user.dto.UserLockUpdateRequest;
import com.otboo.domain.user.dto.UserRoleUpdateRequest;
import com.otboo.domain.user.exception.UserNotFoundException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uk6dotkott2kjsp8vw4d0m25fb7";

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserDto create(UserCreateRequest request) {
    String normalizedEmail = request.email().toLowerCase(Locale.ROOT);

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new DuplicateEmailException(normalizedEmail);
    }

    String passwordHash = passwordEncoder.encode(request.password());
    User user = User.create(normalizedEmail, request.name(), passwordHash);

    User saved;
    try {
      saved = userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      if (isEmailUniqueViolation(e)) {
        throw new DuplicateEmailException(normalizedEmail);
      }
      throw e;
    }

    Profile profile = Profile.createDefault(saved);
    profileRepository.save(profile);

    return UserDto.from(saved);
  }

  @Transactional
  public UserDto changeRole(UUID userId, UserRoleUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    user.changeRole(request.role());

    return UserDto.from(user);
  }

  @Transactional
  public UserDto updateLock(UUID userId, UserLockUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    if (request.locked()) {
      user.lock();
    } else {
      user.unlock();
    }

    return UserDto.from(user);
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    Throwable cause = e.getMostSpecificCause();
    String message = cause.getMessage();
    return message != null && message.toLowerCase().contains(EMAIL_UNIQUE_CONSTRAINT);
  }
}