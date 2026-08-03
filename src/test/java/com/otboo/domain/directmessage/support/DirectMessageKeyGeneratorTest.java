package com.otboo.domain.directmessage.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectMessageKeyGeneratorTest {

  @Test
  @DisplayName("두명의 사용자 ID를 문자열 기준 오름차순으로 정렬해 DM Key 생성 테스트")
  void generate_ordersUserIdsAsc() {
    UUID firstUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID secondUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    String result = DirectMessageKeyGenerator.generate(secondUserId, firstUserId);

    assertThat(result).isEqualTo(
        "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222"
    );
  }

  @Test
  @DisplayName("오름차순 정렬이기에 순서가 바뀌어도 DM Key가 생성되는거 테스트")
  void generate_returnsSameKeyRegardlessOfOrder() {
    UUID firstUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    UUID secondUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    String key1 = DirectMessageKeyGenerator.generate(firstUserId, secondUserId);
    String key2 = DirectMessageKeyGenerator.generate(secondUserId, firstUserId);

    assertThat(key1).isEqualTo(key2);
  }
}