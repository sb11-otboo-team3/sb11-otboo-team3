package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class ClothesRepositoryTest {

    @Autowired
    private ClothesRepository clothesRepository;

    @Test
    void 의상을_저장하면_조회할_수_있다() {
        //given
        Clothes clothes = new Clothes(UUID.randomUUID(), "반팔 티셔츠", null, "TOP");

        //when
        Clothes saved = clothesRepository.save(clothes);

        //then
        assertTrue(clothesRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void 논리_삭제해도_행은_그대로_존재한다() {
        //given
        Clothes clothes = clothesRepository.save(new Clothes(UUID.randomUUID(), "청바지", null, "BOTTOM"));

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
        UUID ownerId = UUID.randomUUID();
        Clothes active = clothesRepository.save(new Clothes(ownerId, "코트", null, "OUTER"));
        Clothes deleted = clothesRepository.save(new Clothes(ownerId, "패딩", null, "OUTER"));
        deleted.delete();
        clothesRepository.saveAndFlush(deleted);

        //when
        List<Clothes> result = clothesRepository.findByOwnerIdAndDeletedAtIsNull(ownerId);

        //then
        assertEquals(1, result.size());
        assertEquals(active.getId(), result.get(0).getId());
    }
}
