package com.otboo.domain.clothes.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.entity.User;
import com.otboo.global.infrastructure.storage.FileStorage;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.C;

@ExtendWith(MockitoExtension.class)
class ClothesMapperTest {

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private ClothesMapper mapper;

    private final User owner = User.create("test@otboo.io", "테스트", "encoded-password");

    @Test
    void imageKey가_있으면_presigned_URL로_변환한다() {
        //given
        Clothes clothes = new Clothes(owner, "티셔츠", "clothes/owner/key.png", ClothesType.TOP);
        given(fileStorage.generateReadUrl("clothes/owner/key.png"))
                .willReturn("https://example.com/clothes/owner/key.png");

        //when
        ClothesResponse response = mapper.toResponse(clothes, List.of(), Map.of());

        //then
        assertThat(response.imageUrl()).isEqualTo("https://example.com/clothes/owner/key.png");
    }

    @Test
    void imageKey가_없으면_URL_변환을_호출하지_않는다() {
        //given
        Clothes clothes = new Clothes(owner, "티셔츠", null, ClothesType.TOP);

        //when
        ClothesResponse response = mapper.toResponse(clothes, List.of(), Map.of());

        //then
        assertThat(response.imageUrl()).isNull();;
        verify(fileStorage, never()).generateReadUrl(any());
    }
}
