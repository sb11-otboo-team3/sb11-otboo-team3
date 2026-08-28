package com.otboo.domain.profile.dto;

import com.otboo.domain.profile.entity.Gender;
import com.otboo.domain.profile.entity.Profile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ProfileDto(
    UUID userId,
    String name,
    Gender gender,
    LocalDate birthDate,
    LocationDto location,
    Integer temperatureSensitivity,
    String profileImageUrl,
    String thumbnailUrl
) {
  public static ProfileDto from(Profile profile, String profileImageUrl, String thumbnailUrl) {
    // 위치를 아직 한 번도 설정하지 않은 프로필은 location 자체를 null로 반환한다.
    LocationDto location =
        profile.getLatitude() == null
            ? null
            : buildLocation(profile);
    return new ProfileDto(
        profile.getUser().getId(),
        profile.getUser().getName(),
        profile.getGender(),
        profile.getBirthDate(),
        location,
        profile.getTemperatureSensitivity(),
        profileImageUrl,
        thumbnailUrl
    );
  }

  private static LocationDto buildLocation(Profile profile) {
    List<String> locationNames = new ArrayList<>();
    if (profile.getProvince() != null) {
      locationNames.add(profile.getProvince());
    }
    if (profile.getCity() != null) {
      locationNames.add(profile.getCity());
    }
    if (profile.getDistrict() != null) {
      locationNames.add(profile.getDistrict());
    }
    return new LocationDto(
        profile.getLatitude(),
        profile.getLongitude(),
        profile.getX(),
        profile.getY(),
        locationNames
    );
  }
}