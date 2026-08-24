package com.otboo.domain.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.config.JpaAuditingConfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ClothesRepositoryImplTest {

    @Autowired
    private ClothesRepository clothesRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void 커서_기준_최신순으로_다음_페이지를_가져온다() {
        //given - createdAt을 명시적으로 다르게 지정해 저장 시점 순서에 의존하지 않게 한다
        User owner = userRepository.save(User.create("owner-cursor@test.com", "tester", "hash"));

        Clothes first = clothesRepository.save(new Clothes(owner, "1번", null, ClothesType.TOP));
        Clothes second = clothesRepository.save(new Clothes(owner, "2번", null, ClothesType.TOP));
        Clothes third = clothesRepository.save(new Clothes(owner, "3번", null, ClothesType.TOP));
        em.flush();

        Instant base = Instant.now();
        updateCreatedAt(first.getId(), base);
        updateCreatedAt(second.getId(), base.plusSeconds(1));
        updateCreatedAt(third.getId(), base.plusSeconds(2));
        em.clear();

        //when - 커서 없이 첫 페이지(limit 2) 조회
        List<Clothes> firstPage =
                clothesRepository.findClothesList(owner.getId(), null, null, null, 2);

        //then
        assertThat(firstPage).extracting(Clothes::getId)
                .containsExactly(third.getId(), second.getId());

        //when - 마지막 항목을 커서로 다음 페이지 조회
        Clothes cursorItem = firstPage.get(1);
        List<Clothes> secondPage = clothesRepository.findClothesList(
                owner.getId(), null, cursorItem.getCreatedAt(), cursorItem.getId(), 2);

        //then
        assertThat(secondPage).extracting(Clothes::getId)
                .containsExactly(first.getId());
    }

    @Test
    void createdAt이_동일해도_id로_타이브레이크해서_중복_누락_없이_페이지네이션한다() {
        //given
        User owner = userRepository.save(User.create("owner-tie@test.com", "tester", "hash"));

        Clothes a = clothesRepository.save(new Clothes(owner, "A", null, ClothesType.TOP));
        Clothes b = clothesRepository.save(new Clothes(owner, "B", null, ClothesType.TOP));
        em.flush();

        Instant sameInstant = Instant.now();
        updateCreatedAt(a.getId(), sameInstant);
        updateCreatedAt(b.getId(), sameInstant);
        em.clear();

        // DB(UUID 컬럼)의 실제 정렬 순서를 기준으로 삼는다.
        // Java UUID.compareTo()는 부호 있는 long 비교라 DB의 UUID 정렬과 순서가 다를 수 있어
        // Java 쪽에서 순서를 미리 예측하지 않고, 커서 없는 조회 결과를 기준값으로 사용한다.
        List<Clothes> all = clothesRepository.findClothesList(owner.getId(), null, null, null, 2);
        assertThat(all).hasSize(2);
        Clothes firstOfAll = all.get(0);
        UUID expectedSecondId = all.get(1).getId();

        //when - 첫 페이지(limit 1)
        List<Clothes> firstPage =
                clothesRepository.findClothesList(owner.getId(), null, null, null, 1);

        //then
        assertThat(firstPage).extracting(Clothes::getId)
                .containsExactly(firstOfAll.getId());

        //when - 커서로 다음 페이지 조회
        // DB에 실제 저장된 createdAt(마이크로초 단위로 반올림/절삭될 수 있음)을 그대로 커서로 써야 한다.
        // 테스트에서 만든 Instant 원본을 그대로 쓰면 정밀도 차이로 동일 값이 아니게 될 수 있다.
        List<Clothes> secondPage = clothesRepository.findClothesList(
                owner.getId(), null, firstOfAll.getCreatedAt(), firstOfAll.getId(), 1);

        //then - 남은 항목이 중복·누락 없이 나온다
        assertThat(secondPage).extracting(Clothes::getId)
                .containsExactly(expectedSecondId);
    }

    private void updateCreatedAt(UUID clothesId, Instant createdAt) {
        em.getEntityManager()
                .createNativeQuery("update clothes set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, clothesId)
                .executeUpdate();
    }
}
