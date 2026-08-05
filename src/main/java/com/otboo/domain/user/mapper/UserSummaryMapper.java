package com.otboo.domain.user.mapper;

import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserSummaryMapper {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;

  public UserSummaryMapper(UserRepository userRepository, ProfileRepository profileRepository) {
    this.userRepository = userRepository;
    this.profileRepository = profileRepository;
  }

  public UserSummary toUserSummary(User user) {
    if (user == null) {
      return null;
    }
    Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
    return new UserSummary(
        user.getId(),
        user.getName(),
        resolveImageUrl(profile)
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
            resolveImageUrl(profileMap.get(user.getId()))
        ))
        .toList();
  }

  private String resolveImageUrl(Profile profile) {
    if (profile == null) {
      return null;
    }
    // TODO: #81 공통 S3 모듈 및 imageUrl -> imageKey 컬럼 변경 완료 후,
    // profile.getImageKey() 기반 Presigned GET URL 생성 로직으로 교체
    return profile.getImageUrl();
  }
}