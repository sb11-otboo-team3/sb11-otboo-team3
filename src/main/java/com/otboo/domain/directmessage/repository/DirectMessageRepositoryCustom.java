package com.otboo.domain.directmessage.repository;

import com.otboo.domain.directmessage.entity.DirectMessage;
import java.util.List;
import java.util.UUID;

public interface DirectMessageRepositoryCustom {

  // 특정 dmKey를 사용하는 방의 메세지를 가져오는 메서드
  List<DirectMessage> findDirectMessages(
      String dmKey,
      String cursor,
      UUID idAfter,
      int limit
  );

  // 특정 dmKey를 사용하는 방의 메세지 개수를 세는 메서드
  long countDirectMessages(String dmKey);
}
