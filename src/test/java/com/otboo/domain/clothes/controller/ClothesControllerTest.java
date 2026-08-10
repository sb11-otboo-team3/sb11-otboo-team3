package com.otboo.domain.clothes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.domain.clothes.dto.response.ClothesListResponse;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.exception.ClothesNotFoundException;
import com.otboo.domain.clothes.service.ClothesService;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.SecurityConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ClothesController.class)
@Import(SecurityConfig.class)
class ClothesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClothesService clothesService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserRepository userRepository;

    private RequestPostProcessor asUser(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @Test
    void 의상_등록_성공하면_201을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(userId, "티셔츠", ClothesType.TOP, List.of());
        ClothesResponse response = new ClothesResponse(
                UUID.randomUUID(), userId, "티셔츠", null, ClothesType.TOP, List.of()
        );

        given(clothesService.create(any(), any(), any())).willReturn(response);

        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)
        );

        // when & then
        mockMvc.perform(multipart("/api/clothes")
                        .file(requestPart)
                        .with(asUser(userId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("티셔츠"));
    }

    @Test
    void 인증_없이_목록_조회하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/clothes")
                        .param("ownerId", UUID.randomUUID().toString())
                        .param("limit", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 목록_조회_성공하면_200을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        ClothesListResponse response = new ClothesListResponse(
                List.of(), null, null, false, 0, "createdAt", "DESCENDING"
        );

        given(clothesService.getList(any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/clothes")
                        .param("ownerId", userId.toString())
                        .param("limit", "20")
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 본인_옷장이_아닌_목록을_조회하면_403을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        given(clothesService.getList(any(), any(), any(), any(), any(), any(Integer.class)))
                .willThrow(new AccessDeniedException("본인 옷장만 조회할 수 있습니다."));

        // when & then
        mockMvc.perform(get("/api/clothes")
                        .param("ownerId", UUID.randomUUID().toString())
                        .param("limit", "20")
                        .with(asUser(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 의상_수정_성공하면_200을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        ClothesUpdateRequest request = new ClothesUpdateRequest("새이름", ClothesType.BOTTOM, List.of(), null);
        ClothesResponse response = new ClothesResponse(
                clothesId, userId, "새이름", null, ClothesType.BOTTOM, List.of()
        );

        given(clothesService.update(any(), any(), any())).willReturn(response);

        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)
        );

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId)
                        .file(requestPart)
                        .with(asUser(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"));
    }

    @Test
    void 존재하지_않는_의상을_수정하면_400을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        ClothesUpdateRequest request = new ClothesUpdateRequest("새이름", ClothesType.TOP, List.of(), null);

        given(clothesService.update(any(), any(), any()))
                .willThrow(new ClothesNotFoundException(clothesId));

        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)
        );

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId)
                        .file(requestPart)
                        .with(asUser(userId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 의상_삭제_성공하면_204를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/clothes/{clothesId}", clothesId)
                        .with(asUser(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 본인_소유가_아닌_의상을_삭제하면_403을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();

        org.mockito.BDDMockito.willThrow(new AccessDeniedException("본인 의상만 삭제할 수 있습니다."))
                .given(clothesService).delete(any(), any());

        // when & then
        mockMvc.perform(delete("/api/clothes/{clothesId}", clothesId)
                        .with(asUser(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}