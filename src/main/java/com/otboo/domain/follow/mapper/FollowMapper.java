package com.otboo.domain.follow.mapper;

import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FollowMapper {

  private final UserSummaryMapper userSummaryMapper;

  public FollowDto toDto(Follow follow) {
    return new FollowDto(
        follow.getId(),
        userSummaryMapper.toUserSummary(follow.getFollowee()),
        userSummaryMapper.toUserSummary(follow.getFollower())
    );
  }
}