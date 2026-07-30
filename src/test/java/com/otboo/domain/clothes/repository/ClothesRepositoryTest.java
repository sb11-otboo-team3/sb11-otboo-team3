package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.config.JpaAuditingConfig;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class ClothesRepositoryTest {

    @Autowired
    private ClothesRepository clothesRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 의상을_저장하면_조회할_수_있다() {
        //given
        User owner = userRepository.save(User.create(
                "owner1@test.com", "철수", "hash"));

        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "반팔 티셔츠", null, ClothesType.TOP));

        //when
        Clothes saved = clothesRepository.save(clothes);

        //then
        assertTrue(clothesRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void 논리_삭제해도_행은_그대로_존재한다() {
        //given
        User owner = userRepository.save(User.create(
                "owner2@test.com", "철수", "hash"));

        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "청바지", null, ClothesType.BOTTOM));

        //when
        clothes.delete();
        clothesRepository.saveAndFlush(clothes);

        //then
        Clothes found = clothesRepository.findById(clothes.getId()).orElseThrow();
        assertNotNull(found.getDeletedAt());
    }

    @Test
    void findByOwnerIdAndDeletedAtIsNull은_논리_삭제된_의상을_제외한다() {
        //given
        User owner = userRepository.save(User.create(
                "owner3@test.com", "철수", "hash"));
        Clothes active = clothesRepository.save(
                new Clothes(owner, "코트", null, ClothesType.OUTER));
        Clothes deleted = clothesRepository.save(
                new Clothes(owner, "패딩", null, ClothesType.OUTER));
        deleted.delete();
        clothesRepository.saveAndFlush(deleted);

        //when
        List<Clothes> result = clothesRepository.findByOwner_IdAndDeletedAtIsNull(owner.getId());

        //then
        assertEquals(1, result.size());
        assertEquals(active.getId(), result.get(0).getId());
    }
}
