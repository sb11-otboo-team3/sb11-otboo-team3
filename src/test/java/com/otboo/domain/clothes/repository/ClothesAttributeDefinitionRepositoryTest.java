package com.otboo.domain.clothes.repository;


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
public class ClothesAttributeDefinitionRepositoryTest {

    @Autowired
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Test
    void 속성_정의를_저장하면_조회할_수_있다() {
        //given
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");

        //when
        ClothesAttributeDefinition saved = definitionRepository.save(definition);

        //then
        assertTrue(definitionRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void findByName으로_조회할_수_있다() {
        //given
        definitionRepository.save(new ClothesAttributeDefinition("소재"));

        //when
        Optional<ClothesAttributeDefinition> found = definitionRepository.findByName("소재");

        //then
        assertTrue(found.isPresent());
    }

    @Test
    void 논리_삭제된_행이_있어도_같은_이름으로_새로_저장하면_유니크_제약_위반이_발생한다() {
        //given
        ClothesAttributeDefinition definition = definitionRepository.save(new ClothesAttributeDefinition("패턴"));
        definition.delete();
        definitionRepository.saveAndFlush(definition);

        //when&then
        assertThrows(DataIntegrityViolationException.class, () ->
                definitionRepository.saveAndFlush(new ClothesAttributeDefinition("패턴"))
        );
    }
}
