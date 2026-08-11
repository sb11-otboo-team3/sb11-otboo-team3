package com.otboo.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.entity.User;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OutfitCombinationRuleTest {

    private final OutfitCombinationRule rule = new OutfitCombinationRule();
    private final User owner = User.create("test@otboo.io", "테스트", "encoded-password");

    @Test
    void 원피스_후보가_있으면_상의와_하의가_제거된다() {
        //given
        Map<ClothesType, List<Clothes>> input = new EnumMap<>(ClothesType.class);
        input.put(ClothesType.DRESS, List.of(new Clothes(owner, "원피스", null, ClothesType.DRESS)));
        input.put(ClothesType.TOP, List.of(new Clothes(owner, "티셔츠", null, ClothesType.TOP)));
        input.put(ClothesType.BOTTOM, List.of(new Clothes(owner, "바지", null, ClothesType.BOTTOM)));
        input.put(ClothesType.OUTER, List.of(new Clothes(owner, "코트", null, ClothesType.OUTER)));

        //when
        Map<ClothesType, List<Clothes>> result = rule.apply(input);

        //then
        assertThat(result).doesNotContainKeys(ClothesType.TOP, ClothesType.BOTTOM);
        assertThat(result).containsKeys(ClothesType.DRESS, ClothesType.OUTER);
    }

    @Test
    void 원피스_후보가_없으면_그대로_반환된다() {
        //given
        Map<ClothesType, List<Clothes>> input = new EnumMap<>(ClothesType.class);
        input.put(ClothesType.TOP, List.of(new Clothes(owner, "티셔츠", null, ClothesType.TOP)));
        input.put(ClothesType.BOTTOM, List.of(new Clothes(owner, "바지", null, ClothesType.BOTTOM)));

        //when
        Map<ClothesType, List<Clothes>> result = rule.apply(input);

        //then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void 원피스_키는_있지만_리스트가_비어있으면_상의_하의를_유지한다() {
        //given
        Map<ClothesType, List<Clothes>> input = new EnumMap<>(ClothesType.class);
        input.put(ClothesType.DRESS, List.of());
        input.put(ClothesType.TOP, List.of(new Clothes(owner, "티셔츠", null, ClothesType.TOP)));

        //when
        Map<ClothesType, List<Clothes>> result = rule.apply(input);

        //then
        assertThat(result).containsKey(ClothesType.TOP);
    }
}