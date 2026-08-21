package com.otboo.domain.user.mapper;

import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.infrastructure.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserSummaryMapper {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final FileStorage fileStorage;

  public UserSummaryMapper(
      UserRepository userRepository,
      ProfileRepository profileRepository,
      FileStorage fileStorage
  ) {
    this.userRepository = userRepository;
    this.profileRepository = profileRepository;
    this.fileStorage = fileStorage;
  }

  public UserSummary toUserSummary(User user) {
    if (user == null) {
      return null;
    }
    Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
    return new UserSummary(
        user.getId(),
        user.getName(),
        resolveThumbnailUrl(profile)
    );
  }

  public List<UserSummary> toUserSummaries(List<UUID> userIds) {
    List<User> users = userRepository.findAllById(userIds);
    Map<UUID, Profile> profileMap = profileRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(Profile::getUserId, p -> p));

    return users.stream()
        .map(user -> new UserSummary(
            user.getId(),
            user.getName(),
            resolveThumbnailUrl(profileMap.get(user.getId()))
        ))
        .toList();
  }

  // 목록 화면 등 여러 사용자를 동시에 보여주는 곳에서는 원본 대신
  // 썸네일을 사용해 불필요한 전송량을 줄인다. 아직 썸네일이 없는
  // (예전에 생성된) 프로필은 원본으로 대체해서 최소한 이미지가
  // 안 보이는 상황은 피한다. (#186)
  private String resolveThumbnailUrl(Profile profile) {
    if (profile == null) {
      return null;
    }
    String key = profile.getThumbnailKey() != null && !profile.getThumbnailKey().isBlank()
        ? profile.getThumbnailKey()
        : profile.getImageKey();

    if (key == null || key.isBlank()) {
      return null;
    }
    return fileStorage.generateReadUrl(key);
  }
}