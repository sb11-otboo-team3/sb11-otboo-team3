package com.otboo.domain.clothes.extraction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.extraction.exception.UnsupportedShoppingMallException;
import com.otboo.domain.clothes.extraction.service.ClothesExtractionService;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.SecurityConfig;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ClothesExtractionController.class)
@Import(SecurityConfig.class)
class ClothesExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClothesExtractionService clothesExtractionService;

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
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/clothes/extractions")
                        .param("url", "https://www.musinsa.com/products/6841401"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void url_파라미터가_없으면_400을_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/clothes/extractions")
                        .with(asUser(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 정상_요청이면_추출_결과를_200으로_반환한다() throws Exception {
        //given
        UUID userId = UUID.randomUUID();
        String url = "https://www.musinsa.com/products/6841401";
        ClothesResponse response = new ClothesResponse(
                null, userId, "반팔 티셔츠", "https://image.msscdn.net/a.jpg", ClothesType.TOP, List.of()
        );

        given(clothesExtractionService.extract(url, userId)).willReturn(response);

        //when & then
        mockMvc.perform(get("/api/clothes/extractions")
                        .param("url", url)
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("반팔 티셔츠"))
                .andExpect(jsonPath("$.type").value("TOP"))
                .andExpect(jsonPath("$.ownerId").value(userId.toString()));
    }

    @Test
    void 미지원_쇼핑몰이면_400을_반환한다() throws Exception {
        //given
        UUID userId = UUID.randomUUID();
        String url = "https://www.coupang.com/products/123";

        given(clothesExtractionService.extract(url, userId))
                .willThrow(new UnsupportedShoppingMallException("www.coupang.com"));

        //when & then
        mockMvc.perform(get("/api/clothes/extractions")
                        .param("url", url)
                        .with(asUser(userId)))
                .andExpect(status().isBadRequest());
    }
}
