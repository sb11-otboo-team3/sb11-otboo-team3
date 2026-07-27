package com.otboo.domain.location.entity;

import com.otboo.domain.location.dto.WeatherAPILocation;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location extends BaseEntity {

  @Column(name = "x", nullable = false)
  private int x;

  @Column(name = "y", nullable = false)
  private int y;

  // 시/도
  @Column(name = "province", nullable = false)
  private String province;

  // 시/군/구
  @Column(name = "city", nullable = false)
  private String city;

  // 읍/면/동
  @Column(name = "district", nullable = false)
  private String district;

  @Column(name = "last_request_at", nullable = false)
  private Instant lastRequestAt;



  @Builder
  private Location(int x, int y, String province, String city, String district) {
    this.x = x;
    this.y = y;
    this.province = province;
    this.city = city;
    this.district = district;
  }

  public WeatherAPILocation toDto(double latitude, double longitude) {
    return new WeatherAPILocation(
        latitude,
        longitude,
        x,
        y,
        new String[]{province, city, district}
    );
  }













}
