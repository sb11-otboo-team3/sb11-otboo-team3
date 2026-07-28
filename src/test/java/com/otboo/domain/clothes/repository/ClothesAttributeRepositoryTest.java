package com.otboo.domain.clothes.repository;


import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class ClothesAttributeRepositoryTest {

    @Autowired
    private ClothesAttributeRepository clothesAttributeRepository;

    @Autowired
    private ClothesRepository clothesRepository;

    @Autowired
    private  ClothesAttributeDefinitionRepository definitionRepository;

    @Test
    void 의상_속성을_저장하면_조회할_수_있다() {
        //given
        Clothes clothes = clothesRepository.save(new Clothes(UUID.randomUUID(), "반팔 티셔츠", null, "TOP"));
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("색상"));
        ClothesAttribute attribute = new ClothesAttribute(clothes, definition, "빨강");

        //when
        ClothesAttribute saved = clothesAttributeRepository.save(attribute);

        //then
        assertTrue(clothesAttributeRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void findByClothes로_해당_의상의_속성_목록을_조회할_수_있다() {
        //given
        Clothes clothes = clothesRepository.save(new Clothes(UUID.randomUUID(), "청바지", null, "BOTTOM"));
        ClothesAttributeDefinition colorDefinition = definitionRepository.save(new ClothesAttributeDefinition("색상"));
        ClothesAttributeDefinition materialDefinition = definitionRepository.save(new ClothesAttributeDefinition("소재"));
        clothesAttributeRepository.save(new ClothesAttribute(clothes, colorDefinition, "파랑"));
        clothesAttributeRepository.save(new ClothesAttribute(clothes, materialDefinition, "데님"));

        //when
        List<ClothesAttribute> result = clothesAttributeRepository.findByClothes(clothes);

        //then
        assertEquals(2, result.size());
    }

    @Test
    void 같은_의상에_같은_속성_정의를_중복_저장하면_유니크_제약_위반이_발생한다() {
        //given
        Clothes clothes = clothesRepository.save(new Clothes(UUID.randomUUID(), "코트", null, "OUTER"));
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("패턴"));
        clothesAttributeRepository.saveAndFlush(new ClothesAttribute(clothes, definition, "체크"));

        //when&then
        assertThrows(DataIntegrityViolationException.class, () ->
                clothesAttributeRepository.saveAndFlush(new ClothesAttribute(clothes, definition, "스트라이프"))
        );
    }

}
