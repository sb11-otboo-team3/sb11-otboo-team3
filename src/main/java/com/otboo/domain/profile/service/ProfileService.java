package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.LocationResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final LocationResolver locationResolver;

  public ProfileDto getProfile(UUID userId) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    return ProfileDto.from(profile);
  }

  @Transactional
  public ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request) {
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

    if (request.location() != null
        && request.location().latitude() != null
        && request.location().longitude() != null) {
      // ProfileService는 그냥 블로킹 트랜잭션 서비스라 여기선 바로 block()으로 값을 꺼내 쓴다.
      WeatherAPILocation location = locationResolver.resolve(
          request.location().latitude(), request.location().longitude()
      ).block();
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