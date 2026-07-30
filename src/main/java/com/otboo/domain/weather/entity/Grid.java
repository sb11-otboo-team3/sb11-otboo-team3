package com.otboo.domain.weather.entity;

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
@Table(name = "grids", uniqueConstraints = @UniqueConstraint(columnNames = {"x", "y"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Grid extends BaseEntity {

  @Column(name = "x", nullable = false, updatable = false)
  private int x;

  @Column(name = "y", nullable = false, updatable = false)
  private int y;

  //최근 접근 시간(날씨 가져오기 배치)
  @Column(name = "last_requested_at", nullable = false)
  private Instant lastRequestedAt;

  @Builder
  private Grid(int x, int y) {
    this.x = x;
    this.y = y;
    this.lastRequestedAt = Instant.now();
  }

  // 이 격자로 요청이 들어오면 최근 요청 시각을 갱신 (날씨 프리패치 배치가 실제로 쓰이는 격자만 골라내는 기준)
  public void refreshRequestedAt() {
    this.lastRequestedAt = Instant.now();
  }
}