package com.otboo.domain.user.repository;

import com.otboo.domain.user.entity.User;
import java.util.List;
import java.util.UUID;

public interface UserRepositoryCustom {
  List<User> findUsers(
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      String sortDirection,
      String emailLike,
      String roleEqual,
      Boolean locked
  );

  long countUsers(
      String emailLike,
      String roleEqual,
      Boolean locked
  );
}