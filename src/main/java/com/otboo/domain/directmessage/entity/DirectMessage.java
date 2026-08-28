package com.otboo.domain.directmessage.entity;

import com.otboo.domain.user.entity.User;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "direct_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id", nullable = true)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receiver_id", nullable = true)
  private User receiver;

  @Column(name = "dm_key", nullable = false, length = 73)
  private String dmKey;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  private DirectMessage(User sender, User receiver, String dmKey, String content) {
    this.sender = sender;
    this.receiver = receiver;
    this.dmKey = dmKey;
    this.content = content;
  }

  public static DirectMessage create(User sender, User receiver, String dmKey, String content) {
    return new DirectMessage(sender, receiver, dmKey, content);
  }
}
