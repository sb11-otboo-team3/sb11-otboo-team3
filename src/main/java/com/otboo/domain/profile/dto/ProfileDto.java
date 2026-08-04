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
    String profileImageUrl
) {

  public static ProfileDto from(Profile profile) {
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

    LocationDto location = new LocationDto(
        profile.getLatitude(),
        profile.getLongitude(),
        profile.getX(),
        profile.getY(),
        locationNames
    );

    return new ProfileDto(
        profile.getUser().getId(),
        profile.getUser().getName(),
        profile.getGender(),
        profile.getBirthDate(),
        location,
        profile.getTemperatureSensitivity(),
        profile.getImageUrl()
    );
  }
}