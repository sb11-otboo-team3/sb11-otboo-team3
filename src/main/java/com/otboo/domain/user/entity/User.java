package com.otboo.domain.user.entity;

import com.otboo.global.common.entity.UpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends UpdatableEntity {

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(nullable = false)
  private boolean locked;

  @Column(name = "token_version", nullable = false)
  private long tokenVersion;

  @Builder
  private User(String email, String name, String passwordHash) {
    this.email = email;
    this.name = name;
    this.passwordHash = passwordHash;
    this.role = UserRole.USER;
    this.locked = false;
    this.tokenVersion = 0L;
  }

  public static User create(String email, String name, String passwordHash) {
    return User.builder()
        .email(email)
        .name(name)
        .passwordHash(passwordHash)
        .build();
  }

  public void changeRole(UserRole newRole) {
    if (this.role != newRole) {
      this.role = newRole;
      this.tokenVersion++;
    }
  }

  public void lock() {
    if (!this.locked) {
      this.locked = true;
      this.tokenVersion++;
    }
  }

  public void unlock() {
    if (this.locked) {
      this.locked = false;
      this.tokenVersion++;
    }
  }

  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.tokenVersion++;
  }
}