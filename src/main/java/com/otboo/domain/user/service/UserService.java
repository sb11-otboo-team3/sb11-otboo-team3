package com.otboo.domain.user.service;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.dto.UserCreateRequest;
import com.otboo.domain.user.dto.UserDto;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.DuplicateEmailException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.otboo.domain.user.dto.UserLockUpdateRequest;
import com.otboo.domain.user.dto.UserRoleUpdateRequest;
import com.otboo.domain.user.exception.UserNotFoundException;
import java.util.UUID;
import com.otboo.domain.user.dto.UserDtoCursorResponse;
import com.otboo.domain.user.exception.InvalidUserCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uk6dotkott2kjsp8vw4d0m25fb7";
  private static final Set<String> VALID_SORT_BY = Set.of("createdAt", "email");
  private static final Set<String> VALID_SORT_DIRECTION = Set.of("ASCENDING", "DESCENDING");

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;

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

    UserRole previousRole = user.getRole();

    user.changeRole(request.role());

    // ADMIN > User일때 WARNING, User > ADMIN일때 INFO
    if (previousRole != user.getRole()) {
      NotificationLevel level = user.getRole() == UserRole.USER
          ? NotificationLevel.WARNING
          : NotificationLevel.INFO;

      eventPublisher.publishEvent(
          new NotificationEvent(
              user.getId(),
              "권한이 변경되었습니다.",
              "회원님의 권한이 " + user.getRole().name() + "로 변경되었습니다.",
              level
          )
      );
    }

    return UserDto.from(user);
  }

  @Transactional
  public UserDto updateLock(UUID userId, UserLockUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    // 활성상태 조회
    boolean previousLocked = user.isLocked();

    if (request.locked()) {
      user.lock();
    } else {
      user.unlock();
    }

    // 활성 상태가 변경될시 실행
    if (previousLocked != user.isLocked()) {
      NotificationLevel level = user.isLocked()
          ? NotificationLevel.WARNING
          : NotificationLevel.INFO;

      String content = user.isLocked()
          ? "회원님의 계정이 비활성 처리되었습니다."
          : "회원님의 계정이 활성 처리되었습니다.";

      eventPublisher.publishEvent(
          new NotificationEvent(
              user.getId(),
              "계정 상태가 변경되었습니다.",
              content,
              level
          )
      );
    }

    return UserDto.from(user);
  }

  public UserDtoCursorResponse getUsers(
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      String sortDirection,
      String emailLike,
      String roleEqual,
      Boolean locked
  ) {
    validateSort(sortBy, sortDirection);
    validateCursor(cursor, idAfter, sortBy);

    List<User> users = userRepository.findUsers(
        cursor, idAfter, limit + 1, sortBy, sortDirection, emailLike, roleEqual, locked
    );

    boolean hasNext = users.size() > limit;
    if (hasNext) {
      users = users.subList(0, limit);
    }

    List<UserDto> data = users.stream()
        .map(UserDto::from)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      User last = users.get(users.size() - 1);
      nextCursor = "createdAt".equalsIgnoreCase(sortBy)
          ? last.getCreatedAt().toString()
          : last.getEmail();
      nextIdAfter = last.getId();
    }

    long totalCount = userRepository.countUsers(emailLike, roleEqual, locked);

    return new UserDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }

  // cursor와 idAfter는 둘 다 있거나 둘 다 없어야 함
  private void validateCursor(String cursor, UUID idAfter, String sortBy) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidUserCursorException();
    }

    if (hasCursor && "createdAt".equalsIgnoreCase(sortBy)) {
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException e) {
        throw new InvalidUserCursorException();
      }
    }
  }

  private void validateSort(String sortBy, String sortDirection) {
    boolean validSortBy = "createdAt".equalsIgnoreCase(sortBy) || "email".equalsIgnoreCase(sortBy);
    boolean validSortDirection = "ASCENDING".equalsIgnoreCase(sortDirection) || "DESCENDING".equalsIgnoreCase(sortDirection);

    if (!validSortBy || !validSortDirection) {
      throw new InvalidUserCursorException();
    }
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    Throwable cause = e.getMostSpecificCause();
    String message = cause.getMessage();
    return message != null && message.toLowerCase().contains(EMAIL_UNIQUE_CONSTRAINT);
  }
}