package com.otboo.domain.clothes.repository;


import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class AttributeSelectableValueRepositoryTest {

    @Autowired
    private AttributeSelectableValueRepository selectableValueRepository;

    @Autowired
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Test
    void 선택값을_저장하면_조회할_수_있다() {
        //given
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("색상"));
        AttributeSelectableValue value = new AttributeSelectableValue(definition, "빨강", 0);

        //when
        AttributeSelectableValue saved = selectableValueRepository.save(value);

        //then
        assertTrue(selectableValueRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void findByDefinitionAndValue로_조회할_수_있다() {
        //given
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("소재"));
        selectableValueRepository.save(new AttributeSelectableValue(definition, "면", 0));

        //when
        Optional<AttributeSelectableValue> found = selectableValueRepository.findByDefinitionAndValue(definition, "면");

        //then
        assertTrue(found.isPresent());
    }

    @Test
    void 논리_삭제된_선택값이_있어도_같은_정의_같은_값으로_새로_저장하면_유니크_제약_위반이_발생한다() {
        //given
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("패턴"));
        AttributeSelectableValue value = selectableValueRepository.save(new AttributeSelectableValue(definition, "체크", 0));
        value.delete();
        selectableValueRepository.saveAndFlush(value);

        //when&then
        assertThrows(DataIntegrityViolationException.class, () ->
                selectableValueRepository.saveAndFlush(new AttributeSelectableValue(definition, "체크", 1))
        );
    }

    @Test
    void displayOrder가_음수면_저장할_수_없다() {
        //given
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("크기"));
        AttributeSelectableValue value = new AttributeSelectableValue(definition, "L", -1);

        //when&then
        assertThrows(DataIntegrityViolationException.class, () ->
                selectableValueRepository.saveAndFlush(value));
    }
}
