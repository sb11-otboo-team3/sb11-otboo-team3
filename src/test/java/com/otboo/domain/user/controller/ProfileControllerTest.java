package com.otboo.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.dto.LocationDto;
import com.otboo.domain.user.dto.ProfileDto;
import com.otboo.domain.user.exception.ProfileNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.user.service.ProfileService;
import com.otboo.global.security.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProfileService profileService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @WithMockUser
  @DisplayName("프로필 조회 요청이 성공하면 200을 반환한다")
  void getProfileReturns200() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    LocationDto location = new LocationDto(37.5, 127.0, 60, 127, List.of("서울특별시", "강남구"));
    ProfileDto response = new ProfileDto(
        userId, "테스트유저", null, LocalDate.of(2000, 1, 1),
        location, 3, "https://example.com/image.jpg"
    );
    given(profileService.getProfile(userId)).willReturn(response);

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  @DisplayName("존재하지 않는 프로필을 조회하면 404를 반환한다")
  void getProfileWithNonExistentProfileReturns404() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(profileService.getProfile(userId)).willThrow(new ProfileNotFoundException(userId));

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId))
        .andExpect(status().isNotFound());
  }
}