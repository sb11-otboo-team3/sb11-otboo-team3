package com.otboo.domain.weather.entity;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "locations", uniqueConstraints = @UniqueConstraint(columnNames = {"x", "y"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location extends BaseEntity {

  @Column(name = "x", nullable = false, updatable = false)
  private int x;

  @Column(name = "y", nullable = false, updatable = false)
  private int y;

  // 시/도
  @Column(name = "province", nullable = false, updatable = false)
  private String province;

  // 시/군/구
  @Column(name = "city", nullable = false, updatable = false)
  private String city;

  // 읍/면/동
  @Column(name = "district", nullable = false, updatable = false)
  private String district;

  @Column(name = "last_requested_at", nullable = false)
  private Instant lastRequestedAt;

  @Builder
  private Location(int x, int y, String province, String city, String district) {
    this.x = x;
    this.y = y;
    this.province = province;
    this.city = city;
    this.district = district;
    this.lastRequestedAt = Instant.now();
  }


  //해당 구역의 요청이 들어오면 최근 요청 시각을 초기화
  public void refreshRequestedAt() {
    this.lastRequestedAt = Instant.now();
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
