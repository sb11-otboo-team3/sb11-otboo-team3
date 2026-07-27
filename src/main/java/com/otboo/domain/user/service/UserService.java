package com.otboo.domain.user.service;

import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uk6dotkott2kjsp8vw4d0m25fb7";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserDto create(UserCreateRequest request) {
    String normalizedEmail = request.email().toLowerCase();

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

    return UserDto.from(saved);
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    Throwable cause = e.getMostSpecificCause();
    String message = cause.getMessage();
    return message != null && message.toLowerCase().contains(EMAIL_UNIQUE_CONSTRAINT);
  }
}