package com.otboo.domain.user.repository;

import static com.otboo.domain.user.entity.QUser.user;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.exception.InvalidUserCursorException;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserRepositoryImpl implements UserRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public UserRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<User> findUsers(
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      String sortDirection,
      String emailLike,
      String roleEqual,
      Boolean locked
  ) {
    boolean isDesc = "DESCENDING".equalsIgnoreCase(sortDirection);

    return queryFactory
        .selectFrom(user)
        .where(
            filterCondition(emailLike, roleEqual, locked),
            cursorCondition(cursor, idAfter, sortBy, isDesc)
        )
        .orderBy(orderSpecifiers(sortBy, isDesc))
        .limit(limit)
        .fetch();
  }

  private BooleanBuilder filterCondition(String emailLike, String roleEqual, Boolean locked) {
    BooleanBuilder builder = new BooleanBuilder();
    if (emailLike != null && !emailLike.isBlank()) {
      builder.and(user.email.containsIgnoreCase(emailLike));
    }
    if (roleEqual != null && !roleEqual.isBlank()) {
      builder.and(user.role.eq(parseRole(roleEqual)));
    }
    if (locked != null) {
      builder.and(user.locked.eq(locked));
    }
    return builder;
  }

  private UserRole parseRole(String roleEqual) {
    try {
      return UserRole.valueOf(roleEqual.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new InvalidUserCursorException();
    }
  }

  private BooleanExpression cursorCondition(
      String cursor, UUID idAfter, String sortBy, boolean isDesc
  ) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    try {
      if ("createdAt".equalsIgnoreCase(sortBy)) {
        Instant cursorCreatedAt = Instant.parse(cursor);
        return isDesc
            ? user.createdAt.lt(cursorCreatedAt)
              .or(user.createdAt.eq(cursorCreatedAt).and(user.id.lt(idAfter)))
            : user.createdAt.gt(cursorCreatedAt)
              .or(user.createdAt.eq(cursorCreatedAt).and(user.id.gt(idAfter)));
      } else {
        return isDesc
            ? user.email.lt(cursor)
              .or(user.email.eq(cursor).and(user.id.lt(idAfter)))
            : user.email.gt(cursor)
              .or(user.email.eq(cursor).and(user.id.gt(idAfter)));
      }
    } catch (Exception e) {
      throw new InvalidUserCursorException();
    }
  }

  private OrderSpecifier<?>[] orderSpecifiers(String sortBy, boolean isDesc) {
    if ("createdAt".equalsIgnoreCase(sortBy)) {
      return isDesc
          ? new OrderSpecifier<?>[]{user.createdAt.desc(), user.id.desc()}
          : new OrderSpecifier<?>[]{user.createdAt.asc(), user.id.asc()};
    } else if ("email".equalsIgnoreCase(sortBy)) {
      return isDesc
          ? new OrderSpecifier<?>[]{user.email.desc(), user.id.desc()}
          : new OrderSpecifier<?>[]{user.email.asc(), user.id.asc()};
    } else {
      throw new InvalidUserCursorException();
    }
  }

  @Override
  public long countUsers(String emailLike, String roleEqual, Boolean locked) {
    Long count = queryFactory
        .select(user.count())
        .from(user)
        .where(filterCondition(emailLike, roleEqual, locked))
        .fetchOne();
    return count == null ? 0 : count;
  }
}