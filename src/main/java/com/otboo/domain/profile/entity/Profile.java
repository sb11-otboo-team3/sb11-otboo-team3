package com.otboo.domain.profile.entity;

import com.otboo.domain.user.entity.User;
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
import jakarta.persistence.Version;
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

  @Column(name = "image_key")
  private String imageKey;

  @Column(name = "thumbnail_key")
  private String thumbnailKey;

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

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

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
    if (gender != null) {
      this.gender = gender;
    }
    if (birthDate != null) {
      this.birthDate = birthDate;
    }
    if (latitude != null) {
      this.latitude = latitude;
    }
    if (longitude != null) {
      this.longitude = longitude;
    }
    if (x != null) {
      this.x = x;
    }
    if (y != null) {
      this.y = y;
    }
    if (province != null) {
      this.province = province;
    }
    if (city != null) {
      this.city = city;
    }
    if (district != null) {
      this.district = district;
    }
    if (temperatureSensitivity != null) {
      this.temperatureSensitivity = temperatureSensitivity;
    }
  }

  public void updateImageKey(String imageKey) {
    this.imageKey = imageKey;
  }

  public void updateThumbnailKey(String thumbnailKey) {
    this.thumbnailKey = thumbnailKey;
  }
  }