package com.otboo.domain.directmessage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.directmessage.support.DirectMessageKeyGenerator;
import com.otboo.domain.user.entity.User;
import com.otboo.global.config.JpaAuditingConfig;
import com.otboo.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class DirectMessageRepositoryTest {

  @Autowired
  private DirectMessageRepository directMessageRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("DM Key에 해당하는 메시지 조회 테스트")
  void findDirectMessages_filterByDmKey() {
    User sender = saveUser("sender@test.com", "sender");
    User receiver = saveUser("receiver@test.com", "receiver");
    User other = saveUser("other@test.com", "other");

    String dmKey = DirectMessageKeyGenerator.generate(sender.getId(), receiver.getId());
    String otherDmKey = DirectMessageKeyGenerator.generate(sender.getId(), other.getId());

    directMessageRepository.save(DirectMessage.create(sender, receiver, dmKey, "message1"));
    directMessageRepository.save(DirectMessage.create(receiver, sender, dmKey, "message2"));
    directMessageRepository.save(DirectMessage.create(sender, other, otherDmKey, "other"));

    entityManager.flush();
    entityManager.clear();

    List<DirectMessage> result = directMessageRepository.findDirectMessages(
        dmKey,
        null,
        null,
        10
    );

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(DirectMessage::getDmKey)
        .containsOnly(dmKey);
  }

  @Test
  @DisplayName("createdAt이 같은 경우 idAfter 기준으로 다음 메시지를 조회 테스트")
  void findDirectMessages_sameCreatedAt_usesIdAfterCursor() {
    User sender = saveUser("sender_same@test.com", "sender");
    User receiver = saveUser("receiver_same@test.com", "receiver");

    String dmKey = DirectMessageKeyGenerator.generate(sender.getId(), receiver.getId());

    DirectMessage message1 =
        directMessageRepository.save(DirectMessage.create(sender, receiver, dmKey, "message1"));
    DirectMessage message2 =
        directMessageRepository.save(DirectMessage.create(receiver, sender, dmKey, "message2"));
    DirectMessage message3 =
        directMessageRepository.save(DirectMessage.create(sender, receiver, dmKey, "message3"));

    entityManager.flush();

    Instant sameCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
    updateCreatedAt(
        List.of(message1.getId(), message2.getId(), message3.getId()),
        sameCreatedAt
    );

    entityManager.clear();

    List<DirectMessage> allMessages = directMessageRepository.findDirectMessages(
        dmKey,
        null,
        null,
        10
    );

    DirectMessage cursorMessage = allMessages.get(0);

    List<DirectMessage> result = directMessageRepository.findDirectMessages(
        dmKey,
        cursorMessage.getCreatedAt().toString(),
        cursorMessage.getId(),
        10
    );

    assertThat(allMessages).hasSize(3);
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(DirectMessage::getId)
        .containsExactly(
            allMessages.get(1).getId(),
            allMessages.get(2).getId()
        );
  }

  @Test
  @DisplayName("DM Key에 해당하는 메시지 개수 조회 테스트")
  void countDirectMessages_byDmKey() {
    User sender = saveUser("sender_count@test.com", "sender");
    User receiver = saveUser("receiver_count@test.com", "receiver");
    User other = saveUser("other_count@test.com", "other");

    String dmKey = DirectMessageKeyGenerator.generate(sender.getId(), receiver.getId());
    String otherDmKey = DirectMessageKeyGenerator.generate(sender.getId(), other.getId());

    directMessageRepository.save(DirectMessage.create(sender, receiver, dmKey, "message1"));
    directMessageRepository.save(DirectMessage.create(receiver, sender, dmKey, "message2"));
    directMessageRepository.save(DirectMessage.create(sender, other, otherDmKey, "other"));

    entityManager.flush();
    entityManager.clear();

    long count = directMessageRepository.countDirectMessages(dmKey);

    assertThat(count).isEqualTo(2L);
  }

  private User saveUser(String email, String name) {
    User user = User.create(email, name, "password");
    entityManager.persist(user);
    return user;
  }

  private void updateCreatedAt(List<UUID> ids, Instant createdAt) {
    entityManager.createQuery("""
            update DirectMessage dm
            set dm.createdAt = :createdAt
            where dm.id in :ids
            """)
        .setParameter("createdAt", createdAt)
        .setParameter("ids", ids)
        .executeUpdate();

    entityManager.flush();
  }
}