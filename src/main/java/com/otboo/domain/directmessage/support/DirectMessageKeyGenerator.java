package com.otboo.domain.directmessage.support;

import java.util.UUID;

public class DirectMessageKeyGenerator {

  private DirectMessageKeyGenerator() {
  }

  public static String generate(UUID senderId, UUID receiverId) {
    String firstId = senderId.toString();
    String secondId = receiverId.toString();

    if (firstId.compareTo(secondId) < 0) {
      return firstId + "_" + secondId;
    } else {
      return secondId + "_" + firstId;
    }
  }
}
