package com.otboo.domain.directmessage.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectMessageTest {

  @Test
  @DisplayName("DM 생성 성공")
  void create_success() {
    User sender = User.create("sender@test.com", "sender", "password");
    User receiver = User.create("receiver@test.com", "receiver", "password");

    DirectMessage directMessage =
        DirectMessage.create(sender, receiver, "dm-key", "hello");

    assertThat(directMessage.getSender()).isEqualTo(sender);
    assertThat(directMessage.getReceiver()).isEqualTo(receiver);
    assertThat(directMessage.getDmKey()).isEqualTo("dm-key");
    assertThat(directMessage.getContent()).isEqualTo("hello");
  }
}