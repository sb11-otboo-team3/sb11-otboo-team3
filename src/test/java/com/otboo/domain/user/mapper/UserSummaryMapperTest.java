package com.otboo.domain.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.infrastructure.storage.FileStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserSummaryMapperTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProfileRepository profileRepository;

  @InjectMocks
  private UserSummaryMapper userSummaryMapper;

  @Mock
  private FileStorage fileStorage;

  @Test
  @DisplayName("user가 null이면 null을 반환한다")
  void toUserSummaryWithNullUserReturnsNull() throws Exception {
    // when
    UserSummary result = userSummaryMapper.toUserSummary(null);

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("썸네일이 있는 프로필은 썸네일 URL을 사용한다")
  void toUserSummaryWithThumbnailReturnsThumbnailUrl() throws Exception {
    // given
    User user = User.create("summarytest@otboo.io", "요약테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);
    ReflectionTestUtils.setField(profile, "imageKey", "profiles/" + userId + "/abc.jpg");
    ReflectionTestUtils.setField(profile, "thumbnailKey", "profiles/" + userId + "/thumb_abc.jpg");

    given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
    given(fileStorage.generateReadUrl("profiles/" + userId + "/thumb_abc.jpg"))
        .willReturn("https://example.com/presigned-thumbnail-url");

    // when
    UserSummary result = userSummaryMapper.toUserSummary(user);

    // then
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.name()).isEqualTo("요약테스트");
    assertThat(result.profileImageUrl()).isEqualTo("https://example.com/presigned-thumbnail-url");
  }

  @Test
  @DisplayName("썸네일이 없는 프로필(예전 데이터)은 원본 이미지 URL로 대체한다")
  void toUserSummaryWithoutThumbnailFallsBackToImageUrl() throws Exception {
    // given
    User user = User.create("nothumb@otboo.io", "썸네일없음", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);
    ReflectionTestUtils.setField(profile, "imageKey", "profiles/" + userId + "/abc.jpg");
    // thumbnailKey는 설정하지 않음 (예전에 생성된 프로필 상황 재현)

    given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
    given(fileStorage.generateReadUrl("profiles/" + userId + "/abc.jpg"))
        .willReturn("https://example.com/presigned-original-url");

    // when
    UserSummary result = userSummaryMapper.toUserSummary(user);

    // then
    assertThat(result.profileImageUrl()).isEqualTo("https://example.com/presigned-original-url");
  }

  @Test
  @DisplayName("프로필이 없는 유저는 imageUrl이 null인 UserSummary를 반환한다")
  void toUserSummaryWithoutProfileReturnsUserSummaryWithNullImageUrl() throws Exception {
    // given
    User user = User.create("noprofile@otboo.io", "프로필없음", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

    // when
    UserSummary result = userSummaryMapper.toUserSummary(user);

    // then
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.name()).isEqualTo("프로필없음");
    assertThat(result.profileImageUrl()).isNull();
  }

  @Test
  @DisplayName("여러 유저를 한 번에 조회하면 각 유저에 맞는 UserSummary 목록을 반환한다")
  void toUserSummariesReturnsListOfUserSummaries() throws Exception {
    // given
    User user1 = User.create("user1@otboo.io", "유저1", "encoded-password");
    UUID userId1 = UUID.randomUUID();
    ReflectionTestUtils.setField(user1, "id", userId1);

    User user2 = User.create("user2@otboo.io", "유저2", "encoded-password");
    UUID userId2 = UUID.randomUUID();
    ReflectionTestUtils.setField(user2, "id", userId2);

    Profile profile1 = Profile.createDefault(user1);
    ReflectionTestUtils.setField(profile1, "userId", userId1);
    ReflectionTestUtils.setField(profile1, "imageKey", "profiles/" + userId1 + "/abc.jpg");
    ReflectionTestUtils.setField(profile1, "thumbnailKey", "profiles/" + userId1 + "/thumb_abc.jpg");

    List<UUID> userIds = List.of(userId1, userId2);
    given(userRepository.findAllById(userIds)).willReturn(List.of(user1, user2));
    given(profileRepository.findAllById(userIds)).willReturn(List.of(profile1));
    given(fileStorage.generateReadUrl("profiles/" + userId1 + "/thumb_abc.jpg"))
        .willReturn("https://example.com/user1-thumb.jpg");

    // when
    List<UserSummary> result = userSummaryMapper.toUserSummaries(userIds);

    // then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(UserSummary::userId)
        .containsExactlyInAnyOrder(userId1, userId2);

    UserSummary summary1 = result.stream()
        .filter(s -> s.userId().equals(userId1))
        .findFirst()
        .orElseThrow();
    assertThat(summary1.profileImageUrl()).isEqualTo("https://example.com/user1-thumb.jpg");

    UserSummary summary2 = result.stream()
        .filter(s -> s.userId().equals(userId2))
        .findFirst()
        .orElseThrow();
    assertThat(summary2.profileImageUrl()).isNull();
  }

  @Test
  @DisplayName("빈 userId 목록으로 조회하면 빈 목록을 반환한다")
  void toUserSummariesWithEmptyListReturnsEmptyList() throws Exception {
    // given
    given(userRepository.findAllById(List.of())).willReturn(List.of());
    given(profileRepository.findAllById(List.of())).willReturn(List.of());

    // when
    List<UserSummary> result = userSummaryMapper.toUserSummaries(List.of());

    // then
    assertThat(result).isEmpty();
  }
}