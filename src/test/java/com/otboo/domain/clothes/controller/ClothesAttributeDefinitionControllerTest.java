package com.otboo.domain.clothes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.clothes.dto.request.ClothesAttributeDefinitionRequest;
import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.domain.clothes.exception.DuplicateAttributeDefinitionNameException;
import com.otboo.domain.clothes.service.ClothesAttributeDefinitionService;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClothesAttributeDefinitionController.class)
@Import(SecurityConfig.class)
class ClothesAttributeDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClothesAttributeDefinitionService clothesAttributeDefinitionService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "USER")
    void 목록_조회는_인증만_되면_200을_반환한다() throws Exception {
        // given
        ClothesAttributeDefinitionResponse response = new ClothesAttributeDefinitionResponse(UUID.randomUUID(), "색상", List.of("빨강", "파랑"), false, Instant.now());
        given(clothesAttributeDefinitionService.getList("createdAt", "ASCENDING", null))
                .willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/clothes/attribute-defs")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "ASCENDING")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("색상"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자가_등록하면_201을_반환한다() throws Exception {
        // given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of("빨강"), false);
        ClothesAttributeDefinitionResponse response = new ClothesAttributeDefinitionResponse(UUID.randomUUID(), "색상", List.of("빨강"), false, Instant.now());
        given(clothesAttributeDefinitionService.create(any(ClothesAttributeDefinitionRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/clothes/attribute-defs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("색상"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_사용자가_등록하면_403을_반환한다() throws Exception {
        // given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of("빨강"), false);

        // when & then
        mockMvc.perform(post("/api/clothes/attribute-defs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 이름이_빈_문자열이면_400을_반환한다() throws Exception {
        // given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("   ", List.of(), false);

        // when & then
        mockMvc.perform(post("/api/clothes/attribute-defs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 이미_등록된_이름이면_400을_반환한다() throws Exception {
        // given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of(), false);
        given(clothesAttributeDefinitionService.create(any(ClothesAttributeDefinitionRequest.class)))
                .willThrow(new DuplicateAttributeDefinitionNameException("색상"));

        // when & then
        mockMvc.perform(post("/api/clothes/attribute-defs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자가_수정하면_200을_반환한다() throws Exception {
        // given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("톤", List.of("네이비"), false);
        ClothesAttributeDefinitionResponse response = new ClothesAttributeDefinitionResponse(definitionId, "톤", List.of("네이비"), false, Instant.now());
        given(clothesAttributeDefinitionService.update(eq(definitionId), any(ClothesAttributeDefinitionRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/clothes/attribute-defs/{definitionId}", definitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("톤"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 존재하지_않는_정의를_수정하면_400를_반환한다() throws Exception {
        // given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("톤", List.of(), false);
        given(clothesAttributeDefinitionService.update(eq(definitionId), any(ClothesAttributeDefinitionRequest.class)))
                .willThrow(new ClothesAttributeDefinitionNotFoundException(definitionId));

        // when & then
        mockMvc.perform(patch("/api/clothes/attribute-defs/{definitionId}", definitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자가_삭제하면_204를_반환한다() throws Exception {
        // given
        UUID definitionId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/clothes/attribute-defs/{definitionId}", definitionId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_사용자가_삭제하면_403을_반환한다() throws Exception {
        // given
        UUID definitionId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/clothes/attribute-defs/{definitionId}", definitionId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}