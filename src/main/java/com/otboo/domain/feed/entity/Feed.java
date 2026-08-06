package com.otboo.domain.feed.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "feeds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed extends SoftDeletableEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", nullable = true)
  private User author;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "weather_id", nullable = true)
  private Weather weather;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "weather_snapshot", nullable = false, columnDefinition = "jsonb")
  private JsonNode weatherSnapshot;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "like_count", nullable = false)
  private long likeCount;

  @Column(name = "comment_count", nullable = false)
  private int commentCount;

  private Feed(User author, Weather weather, JsonNode weatherSnapshot, String content) {
    this.author = author;
    this.weather = weather;
    this.weatherSnapshot = weatherSnapshot;
    this.content = content;
    this.likeCount = 0L;
    this.commentCount = 0;
  }

  public static Feed create(User author, Weather weather, JsonNode weatherSnapshot, String content) {
    return new Feed(author, weather, weatherSnapshot, content);
  }

  public void updateContent(String content) {
    this.content = content;
  }

  public void increaseLikeCount() {
    this.likeCount++;
  }

  public void decreaseLikeCount() {
    if (this.likeCount > 0) {
      this.likeCount--;
    }
  }

  public void increaseCommentCount() {
    this.commentCount++;
  }
}
