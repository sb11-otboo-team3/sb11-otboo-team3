package com.otboo.domain.clothes.repository;


import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

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

    @Autowired
    private UserRepository userRepository;

    @Test
    void 의상_속성을_저장하면_조회할_수_있다() {
        //given
        User owner = userRepository.save(User.create("owner1@test.com", "철수", "hash"));
        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "반팔 티셔츠", null, ClothesType.TOP));
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
        User owner = userRepository.save(User.create("owner2@test.com", "철수", "hash"));
        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "청바지", null, ClothesType.BOTTOM));
        ClothesAttributeDefinition colorDefinition =
                definitionRepository.save(new ClothesAttributeDefinition("색상"));
        ClothesAttributeDefinition materialDefinition =
                definitionRepository.save(new ClothesAttributeDefinition("소재"));
        clothesAttributeRepository.save(new ClothesAttribute(
                clothes, colorDefinition, "파랑"));
        clothesAttributeRepository.save(new ClothesAttribute(
                clothes, materialDefinition, "데님"));

        //when
        List<ClothesAttribute> result = clothesAttributeRepository.findByClothes(clothes);

        //then
        assertEquals(2, result.size());
    }

    @Test
    void 같은_의상에_같은_속성_정의를_중복_저장하면_유니크_제약_위반이_발생한다() {
        //given
        User owner = userRepository.save(User.create(
                "owner3@test.com", "철수", "hash"));
        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "코트", null, ClothesType.OUTER));
        ClothesAttributeDefinition definition =
                definitionRepository.save(new ClothesAttributeDefinition("패턴"));
        clothesAttributeRepository.saveAndFlush(new ClothesAttribute(
                clothes, definition, "체크"));

        //when&then
        assertThrows(DataIntegrityViolationException.class, () ->
                clothesAttributeRepository.saveAndFlush(new ClothesAttribute(clothes, definition, "스트라이프"))
        );
    }

    @Test
    void 속성_삭제_직후_flush_없이_같은_정의로_재저장하면_유니크_제약_위반이_발생한다() {
        // 버그 재현: Hibernate는 기본적으로 INSERT를 DELETE보다 먼저 flush하므로,
        // deleteByClothes() 다음에 flush 없이 바로 같은 정의로 저장하면 실패한다.
        //given
        User owner = userRepository.save(User.create(
                "owner4@test.com", "철수", "hash"));
        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "셔츠", null, ClothesType.TOP));
        ClothesAttributeDefinition definition =
                definitionRepository.save(new ClothesAttributeDefinition("핏"));
        clothesAttributeRepository.saveAndFlush(new ClothesAttribute(clothes, definition, "슬림"));

        //when
        clothesAttributeRepository.deleteByClothes(clothes);

        //then
        assertThrows(DataIntegrityViolationException.class, () ->
                clothesAttributeRepository.saveAndFlush(new ClothesAttribute(clothes, definition, "오버핏"))
        );
    }

    @Test
    void 속성_삭제_직후_flush하면_같은_정의로_재저장할_수_있다() {
        // 수정 확인: deleteByClothes() 다음에 명시적으로 flush를 호출하면
        // 삭제가 먼저 DB에 반영되어 같은 정의로도 정상 저장된다.
        //given
        User owner = userRepository.save(User.create(
                "owner5@test.com", "철수", "hash"));
        Clothes clothes = clothesRepository.save(new Clothes(
                owner, "셔츠", null, ClothesType.TOP));
        ClothesAttributeDefinition definition =
                definitionRepository.save(new ClothesAttributeDefinition("핏"));
        clothesAttributeRepository.saveAndFlush(new ClothesAttribute(clothes, definition, "슬림"));

        //when
        clothesAttributeRepository.deleteByClothes(clothes);
        clothesAttributeRepository.flush();
        ClothesAttribute saved = clothesAttributeRepository.saveAndFlush(
                new ClothesAttribute(clothes, definition, "오버핏"));

        //then
        List<ClothesAttribute> result = clothesAttributeRepository.findByClothes(clothes);
        assertEquals(1, result.size());
        assertEquals(saved.getId(), result.get(0).getId());
        assertEquals("오버핏", result.get(0).getValue());
    }

}
