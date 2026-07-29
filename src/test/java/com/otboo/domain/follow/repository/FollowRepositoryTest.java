package com.otboo.domain.follow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.user.entity.User;
import com.otboo.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import com.otboo.global.config.JpaAuditingConfig;

@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class FollowRepositoryTest {

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("팔로잉 목록 조회 테스트(이름 오름차순 기준임)")
  void findFollowings_orderByFolloweeNameAsc() {
    User follower = saveUser("follower@test.com", "Follower");
    User charlie = saveUser("charlie@test.com", "Charlie");
    User alice = saveUser("alice@test.com", "Alice");
    User bob = saveUser("bob@test.com", "Bob");

    followRepository.save(Follow.create(follower, charlie));
    followRepository.save(Follow.create(follower, alice));
    followRepository.save(Follow.create(follower, bob));

    entityManager.flush();
    entityManager.clear();

    List<Follow> result = followRepository.findFollowings(
        follower.getId(),
        null,
        null,
        10,
        null
    );

    assertThat(result).hasSize(3);
    assertThat(result)
        .extracting(follow -> follow.getFollowee().getName())
        .containsExactly("Alice", "Bob", "Charlie");
  }

  private User saveUser(String email, String name) {
    User user = User.create(email, name, "password");
    entityManager.persist(user);
    return user;
  }

  @Test
  @DisplayName("팔로잉 목록 조회 테스트 (nameLike로 검색)")
  void findFollowings_filterByNameLike() {
    User follower = saveUser("follower@test.com", "Follower");
    User alice = saveUser("alice@test.com", "Alice");
    User albert = saveUser("albert@test.com", "Albert");
    User bob = saveUser("bob@test.com", "Bob");

    followRepository.save(Follow.create(follower, alice));
    followRepository.save(Follow.create(follower, albert));
    followRepository.save(Follow.create(follower, bob));

    entityManager.flush();
    entityManager.clear();

    List<Follow> result = followRepository.findFollowings(
        follower.getId(),
        null,
        null,
        10,
        "al"
    );

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(follow -> follow.getFollowee().getName())
        .containsExactly("Albert", "Alice");
  }

  @Test
  @DisplayName("nameLike 조건에 맞는 개수를 반환 테스트")
  void countFollowings_withNameLike() {
    User follower = saveUser("follower@test.com", "Follower");
    User alice = saveUser("alice@test.com", "Alice");
    User albert = saveUser("albert@test.com", "Albert");
    User bob = saveUser("bob@test.com", "Bob");

    followRepository.save(Follow.create(follower, alice));
    followRepository.save(Follow.create(follower, albert));
    followRepository.save(Follow.create(follower, bob));

    entityManager.flush();
    entityManager.clear();

    long count = followRepository.countFollowings(follower.getId(), "al");

    assertThat(count).isEqualTo(2L);
  }

  @Test
  @DisplayName("팔로워 목록 조회 테스트(이름 오름차순 기준임)")
  void findFollowers_orderByFollowerNameAsc() {
    User followee = saveUser("followee@test.com", "Followee");
    User charlie = saveUser("charlie@test.com", "Charlie");
    User alice = saveUser("alice@test.com", "Alice");
    User bob = saveUser("bob@test.com", "Bob");

    followRepository.save(Follow.create(charlie, followee));
    followRepository.save(Follow.create(alice, followee));
    followRepository.save(Follow.create(bob, followee));

    entityManager.flush();
    entityManager.clear();

    List<Follow> result = followRepository.findFollowers(
        followee.getId(),
        null,
        null,
        10,
        null
    );

    assertThat(result).hasSize(3);
    assertThat(result)
        .extracting(follow -> follow.getFollower().getName())
        .containsExactly("Alice", "Bob", "Charlie");
  }

  @Test
  @DisplayName("팔로잉 목록 조회 테스트 (nameLike로 검색)")
  void findFollowers_filterByNameLike() {
    User followee = saveUser("followee@test.com", "Followee");
    User alice = saveUser("alice@test.com", "Alice");
    User albert = saveUser("albert@test.com", "Albert");
    User bob = saveUser("bob@test.com", "Bob");

    followRepository.save(Follow.create(alice, followee));
    followRepository.save(Follow.create(albert, followee));
    followRepository.save(Follow.create(bob, followee));

    entityManager.flush();
    entityManager.clear();

    List<Follow> result = followRepository.findFollowers(
        followee.getId(),
        null,
        null,
        10,
        "al"
    );

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(follow -> follow.getFollower().getName())
        .containsExactly("Albert", "Alice");
  }

  @Test
  @DisplayName("nameLike 조건에 맞는 개수를 반환 테스트")
  void countFollowers_withNameLike() {
    User followee = saveUser("followee@test.com", "Followee");
    User alice = saveUser("alice@test.com", "Alice");
    User albert = saveUser("albert@test.com", "Albert");
    User bob = saveUser("bob@test.com", "Bob");

    followRepository.save(Follow.create(alice, followee));
    followRepository.save(Follow.create(albert, followee));
    followRepository.save(Follow.create(bob, followee));

    entityManager.flush();
    entityManager.clear();

    long count = followRepository.countFollowers(followee.getId(), "al");

    assertThat(count).isEqualTo(2L);
  }
}