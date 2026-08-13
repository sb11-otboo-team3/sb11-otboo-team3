package com.otboo.domain.follow.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowMapperTest {

  @Mock
  private UserSummaryMapper userSummaryMapper;

  @InjectMocks
  private FollowMapper followMapper;

  @Test
  @DisplayName("팔로우 단건 DTO 변환 성공 테스트")
  void toDto_success() {
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");
    ReflectionTestUtils.setField(follower, "id", followerId);
    ReflectionTestUtils.setField(followee, "id", followeeId);

    Follow follow = Follow.create(follower, followee);
    ReflectionTestUtils.setField(follow, "id", followId);

    UserSummary followerSummary = new UserSummary(followerId, "follower", "follower-image");
    UserSummary followeeSummary = new UserSummary(followeeId, "followee", "followee-image");

    given(userSummaryMapper.toUserSummary(follower)).willReturn(followerSummary);
    given(userSummaryMapper.toUserSummary(followee)).willReturn(followeeSummary);

    FollowDto result = followMapper.toDto(follow);

    assertThat(result.id()).isEqualTo(followId);
    assertThat(result.follower()).isEqualTo(followerSummary);
    assertThat(result.followee()).isEqualTo(followeeSummary);

    verify(userSummaryMapper).toUserSummary(follower);
    verify(userSummaryMapper).toUserSummary(followee);
  }

  @Test
  @DisplayName("팔로우 목록 DTO 변환 시 사용자 요약을 벌크 조회한다")
  void toDtos_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID firstFollowId = UUID.randomUUID();
    UUID secondFollowId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");
    ReflectionTestUtils.setField(follower, "id", followerId);
    ReflectionTestUtils.setField(followee, "id", followeeId);

    Follow firstFollow = Follow.create(follower, followee);
    Follow secondFollow = Follow.create(followee, follower);
    ReflectionTestUtils.setField(firstFollow, "id", firstFollowId);
    ReflectionTestUtils.setField(secondFollow, "id", secondFollowId);

    UserSummary followerSummary = new UserSummary(followerId, "follower", "follower-image");
    UserSummary followeeSummary = new UserSummary(followeeId, "followee", "followee-image");

    given(userSummaryMapper.toUserSummaries(argThat(userIds ->
        userIds.size() == 2
            && userIds.contains(followerId)
            && userIds.contains(followeeId)
    ))).willReturn(List.of(followerSummary, followeeSummary));

    List<FollowDto> result = followMapper.toDtos(List.of(firstFollow, secondFollow));

    assertThat(result).hasSize(2);

    assertThat(result.get(0).id()).isEqualTo(firstFollowId);
    assertThat(result.get(0).follower()).isEqualTo(followerSummary);
    assertThat(result.get(0).followee()).isEqualTo(followeeSummary);

    assertThat(result.get(1).id()).isEqualTo(secondFollowId);
    assertThat(result.get(1).follower()).isEqualTo(followeeSummary);
    assertThat(result.get(1).followee()).isEqualTo(followerSummary);

    verify(userSummaryMapper).toUserSummaries(argThat(userIds ->
        userIds.size() == 2
            && userIds.contains(followerId)
            && userIds.contains(followeeId)
    ));
  }
}
