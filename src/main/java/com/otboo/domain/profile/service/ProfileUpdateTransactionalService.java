package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


// DB 저장 전용 트랜잭션 빈. ProfileService 안의 메서드로 두지 않고 별도 빈으로 분리한 이유:

@Service
@RequiredArgsConstructor
public class ProfileUpdateTransactionalService {

  private final ProfileRepository profileRepository;

  @Transactional
  public ProfileDto update(UUID userId, ProfileUpdateRequest request, WeatherAPILocation location) {
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
      province = location.locationNames().get(0);
      city = location.locationNames().get(1);
      district = location.locationNames().get(2);
    }

    profile.update(
        request.gender(),
        request.birthDate(),
        latitude, longitude, x, y,
        province, city, district,
        request.temperatureSensitivity()
    );

    return ProfileDto.from(profile);
  }
}
