package com.otboo.domain.follow.mapper;

import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.UserSummary;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.user.entity.User;

public final class FollowMapper {

  private FollowMapper() {
  }

  public static FollowDto toDto(Follow follow) {
    return new FollowDto(
        follow.getId(),
        toUserSummary(follow.getFollowee()),
        toUserSummary(follow.getFollower())
    );
  }

  private static UserSummary toUserSummary(User user) {
    if (user == null) {
      return null;
    }

    return new UserSummary(
        user.getId(),
        user.getName(),
        null
    );
  }
}