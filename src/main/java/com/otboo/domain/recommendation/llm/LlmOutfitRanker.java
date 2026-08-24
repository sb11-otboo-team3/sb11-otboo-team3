package com.otboo.domain.recommendation.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.recommendation.llm.dto.OpenRouterMessage;
import com.otboo.domain.recommendation.llm.dto.OutfitCandidate;
import com.otboo.domain.recommendation.llm.dto.RankedOutfit;
import com.otboo.domain.weather.entity.PrecipitationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmOutfitRanker {

    private static final int OUTFIT_COUNT = 3;

    private final LlmOutfitClient llmOutfitClient;
    private final ObjectMapper objectMapper;

    public Optional<List<RankedOutfit>> rank(
            List<OutfitCandidate> candidates,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(candidates, minTemperature, maxTemperature, precipitationType, temperatureSensitivity);
            String content = llmOutfitClient
                    .requestCompletion(List.of(OpenRouterMessage.user(prompt)))
                    .block()
                    .choices()
                    .get(0)
                    .message()
                    .content();

            List<RankedOutfit> outfits = objectMapper.readValue(content, LlmOutfitsPayload.class).outfits();
            if (outfits.isEmpty() || !allClothesIdsKnown(outfits, candidates)) {
                return Optional.empty();
            }

            return Optional.of(outfits);
        } catch (Exception e) {
            log.warn("LLM 추천 보정 실패, 기존 로직으로 폴백합니다.", e);
            return Optional.empty();
        }
    }

    private boolean allClothesIdsKnown(List<RankedOutfit> outfits, List<OutfitCandidate> candidates) {
        Set<UUID> candidateIds = candidates.stream().map(OutfitCandidate::id).collect(Collectors.toSet());
        return outfits.stream()
                .flatMap(outfit -> outfit.clothesIds().stream())
                .allMatch(candidateIds::contains);
    }

    private String buildPrompt(
            List<OutfitCandidate> candidates,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        String candidateText = candidates.stream()
                .map(c -> "- id=%s, type=%s, name=%s, %s".formatted(c.id(), c.type(), c.name(), c.attributes()))
                .collect(Collectors.joining("\n"));

        return """
                너는 옷 코디 추천가야. 아래 후보 옷들로 %d개의 완성된 코디 조합을 만들어서 좋은 순서대로 반환해.
                각 조합은 서로 다른 type의 옷을 조합해서 구성해.

                날씨: 최저 %.1f도, 최고 %.1f도, 강수 형태 %s
                사용자 추위 민감도(1~5, 높을수록 추위를 많이 탐): %d

                후보 옷 목록:
                %s

                반드시 아래 JSON 형식으로만 답해. 다른 설명은 붙이지 마.
                {"outfits": [{"clothesIds": ["id1", "id2", ...]}, ...]}
                """.formatted(OUTFIT_COUNT, minTemperature, maxTemperature, precipitationType, temperatureSensitivity, candidateText);
    }

    private record LlmOutfitsPayload(List<RankedOutfit> outfits) {
    }
}
