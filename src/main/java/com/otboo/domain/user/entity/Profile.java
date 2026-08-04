package com.otboo.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "profiles")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

  @Id
  private UUID userId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "image_url")
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Gender gender;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  private Double latitude;
  private Double longitude;
  private Integer x;
  private Integer y;
  private String province;
  private String city;
  private String district;

  @Column(name = "temp_sensitivity")
  private Integer temperatureSensitivity;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private Profile(User user) {
    this.user = user;
  }

  public static Profile createDefault(User user) {
    return new Profile(user);
  }

  public void update(
      Gender gender,
      LocalDate birthDate,
      Double latitude,
      Double longitude,
      Integer x,
      Integer y,
      String province,
      String city,
      String district,
      Integer temperatureSensitivity
  ) {
    this.gender = gender;
    this.birthDate = birthDate;
    this.latitude = latitude;
    this.longitude = longitude;
    this.x = x;
    this.y = y;
    this.province = province;
    this.city = city;
    this.district = district;
    this.temperatureSensitivity = temperatureSensitivity;
  }

  public void updateImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }
}