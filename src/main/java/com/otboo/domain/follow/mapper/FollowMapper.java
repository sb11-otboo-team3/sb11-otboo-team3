package com.otboo.domain.follow.mapper;

import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  public List<FollowDto> toDtos(List<Follow> follows) {
    List<UUID> userIds = follows.stream()
        .flatMap(follow -> Stream.of(
            follow.getFollowee().getId(),
            follow.getFollower().getId()
        ))
        .distinct()
        .toList();

    Map<UUID, UserSummary> userSummaryMap = userSummaryMapper.toUserSummaries(userIds).stream()
        .collect(Collectors.toMap(UserSummary::userId, userSummary -> userSummary));

    return follows.stream()
        .map(follow -> new FollowDto(
            follow.getId(),
            userSummaryMap.get(follow.getFollowee().getId()),
            userSummaryMap.get(follow.getFollower().getId())
        ))
        .toList();
  }
}