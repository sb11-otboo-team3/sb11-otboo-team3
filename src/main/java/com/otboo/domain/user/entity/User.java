package com.otboo.domain.user.entity;

import com.otboo.global.common.entity.UpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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

  private User(String email, String name, String passwordHash) {
    this.email = email.toLowerCase(java.util.Locale.ROOT);
    this.name = name;
    this.passwordHash = passwordHash;
    this.role = UserRole.USER;
    this.locked = false;
    this.tokenVersion = 0L;
  }

  public static User create(String email, String name, String passwordHash) {
    return new User(email, name, passwordHash);
  }

  public void changeRole(UserRole newRole) {
    this.role = newRole;
  }

  public void lock() {
    this.locked = true;
  }

  public void unlock() {
    this.locked = false;
  }

  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
  }

  public void changeName(String newName) {
    this.name = newName;
  }
}