package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// DB 저장 전용 트랜잭션 빈
@Service
@RequiredArgsConstructor
public class ProfileUpdateTransactionalService {

  private final ProfileRepository profileRepository;

  @Transactional
  public ProfileDto update(
      UUID userId,
      ProfileUpdateRequest request,
      WeatherAPILocation location,
      String newImageKey,
      String profileImageUrl
  ) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    if (request.name() != null) {
      profile.getUser().changeName(request.name());
    }

    String province = null;
    String city = null;
    String district = null;
    Double latitude = null;
    Double longitude = null;
    Integer x = null;
    Integer y = null;

    if (location != null) {
      latitude = location.latitude();
      longitude = location.longitude();
      x = location.x();
      y = location.y();

      // 세종시처럼 행정구역 단계가 3개 미만일 수 있으므로 안전하게 조회한다.
      province = nameAt(location.locationNames(), 0);
      city = nameAt(location.locationNames(), 1);
      district = nameAt(location.locationNames(), 2);
    }

    profile.update(
        request.gender(),
        request.birthDate(),
        latitude,
        longitude,
        x,
        y,
        province,
        city,
        district,
        request.temperatureSensitivity()
    );

    if (newImageKey != null) {
      profile.updateImageKey(newImageKey);
    }

    return ProfileDto.from(profile, profileImageUrl);
  }

  private String nameAt(List<String> locationNames, int index) {
    return locationNames != null && locationNames.size() > index
        ? locationNames.get(index)
        : null;
  }
}