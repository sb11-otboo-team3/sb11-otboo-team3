package com.otboo.domain.recommendation.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.recommendation.llm.dto.OpenRouterChatResponse;
import com.otboo.domain.recommendation.llm.dto.OpenRouterChatResponse.Choice;
import com.otboo.domain.recommendation.llm.dto.OpenRouterMessage;
import com.otboo.domain.recommendation.llm.dto.OutfitCandidate;
import com.otboo.domain.recommendation.llm.dto.RankedOutfit;
import com.otboo.domain.weather.entity.PrecipitationType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LlmOutfitRankerTest {

    @Mock
    private LlmOutfitClient llmOutfitClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmOutfitRanker ranker;

    private final UUID topId = UUID.randomUUID();
    private final UUID bottomId = UUID.randomUUID();
    private final List<OutfitCandidate> candidates = List.of(
            new OutfitCandidate(topId, ClothesType.TOP, "반팔", List.of("색상: 빨강")),
            new OutfitCandidate(bottomId, ClothesType.BOTTOM, "청바지", List.of("색상: 파랑"))
    );

    @BeforeEach
    void setUp() {
        ranker = new LlmOutfitRanker(llmOutfitClient, objectMapper);
    }

    @Test
    void 정상_응답이면_랭킹된_조합_목록을_반환한다() {
        //given
        String content = """
                {"outfits": [{"clothesIds": ["%s", "%s"]}]}
                """.formatted(topId, bottomId);
        given(llmOutfitClient.requestCompletion(anyList())).willReturn(Mono.just(chatResponse(content)));

        //when
        Optional<List<RankedOutfit>> result = ranker.rank(
                candidates, 5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(new RankedOutfit(List.of(topId, bottomId)));
    }

    @Test
    void 후보에_없는_id가_포함되면_빈_Optional을_반환한다() {
        //given
        String content = """
                {"outfits": [{"clothesIds": ["%s"]}]}
                """.formatted(UUID.randomUUID());
        given(llmOutfitClient.requestCompletion(anyList())).willReturn(Mono.just(chatResponse(content)));

        //when
        Optional<List<RankedOutfit>> result = ranker.rank(
                candidates, 5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 응답의_outfits가_비어있으면_빈_Optional을_반환한다() {
        //given
        given(llmOutfitClient.requestCompletion(anyList()))
                .willReturn(Mono.just(chatResponse("{\"outfits\": []}")));

        //when
        Optional<List<RankedOutfit>> result = ranker.rank(
                candidates, 5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void LLM_호출이_실패하면_빈_Optional을_반환한다() {
        //given
        given(llmOutfitClient.requestCompletion(anyList()))
                .willReturn(Mono.error(new RuntimeException("OpenRouter 호출 실패")));

        //when
        Optional<List<RankedOutfit>> result = ranker.rank(
                candidates, 5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 응답을_파싱할_수_없으면_빈_Optional을_반환한다() {
        //given
        given(llmOutfitClient.requestCompletion(anyList()))
                .willReturn(Mono.just(chatResponse("이건 JSON이 아니다")));

        //when
        Optional<List<RankedOutfit>> result = ranker.rank(
                candidates, 5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 후보가_없으면_LLM을_호출하지_않고_빈_Optional을_반환한다() {
        //when
        Optional<List<RankedOutfit>> result = ranker.rank(
                List.of(), 5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
        verify(llmOutfitClient, never()).requestCompletion(anyList());
    }

    private OpenRouterChatResponse chatResponse(String content) {
        return new OpenRouterChatResponse(List.of(new Choice(OpenRouterMessage.user(content))));
    }
}
