package com.otboo.domain.user.entity;

import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "oauth_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccount extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OAuthProvider provider;

  @Column(name = "provider_user_id", nullable = false)
  private String providerUserId;

  private OAuthAccount(User user, OAuthProvider provider, String providerUserId) {
    this.user = user;
    this.provider = provider;
    this.providerUserId = providerUserId;
  }

  public static OAuthAccount create(User user, OAuthProvider provider, String providerUserId) {
    return new OAuthAccount(user, provider, providerUserId);
  }
}