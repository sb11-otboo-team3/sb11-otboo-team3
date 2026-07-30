package com.otboo.domain.follow.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.user.entity.User;
import org.junit.jupiter.api.Test;

class FollowTest {

  @Test
  void create_success() {
    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");

    Follow follow = Follow.create(follower, followee);

    assertThat(follow.getFollower()).isEqualTo(follower);
    assertThat(follow.getFollowee()).isEqualTo(followee);
  }
}