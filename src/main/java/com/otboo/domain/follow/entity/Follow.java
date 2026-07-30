package com.otboo.domain.follow.entity;

import com.otboo.domain.user.entity.User;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "follows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "follower_id", nullable = false)
  private User follower;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "followee_id", nullable = false)
  private User followee;

  private Follow(User follower, User followee) {
    this.follower = follower;
    this.followee = followee;
  }

  public static Follow create(User follower, User followee) {
    return new Follow(follower, followee);
  }
}