package com.otboo.domain.follow.dto.response;

import com.otboo.domain.follow.entity.Follow;
import java.util.UUID;

public record FollowDto(
    UUID id,
    UserSummary followee,
    UserSummary follower
) {

  public static FollowDto from(Follow follow) {
    return new FollowDto(
        follow.getId(),
        UserSummary.from(follow.getFollowee()),
        UserSummary.from(follow.getFollower())
    );
  }
}