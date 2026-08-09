package com.otboo.domain.profile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.profile.dto.LocationDto;
import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Gender;
import com.otboo.domain.profile.exception.ProfileAccessDeniedException;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.service.ProfileService;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.security.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

  private UsernamePasswordAuthenticationToken authenticationFor(UUID userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );
  }

  @Test
  @DisplayName("본인 프로필 조회 요청이 성공하면 200을 반환한다")
  void getProfileReturns200() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    LocationDto location = new LocationDto(37.5, 127.0, 60, 127, List.of("서울특별시", "강남구"));
    ProfileDto response = new ProfileDto(
        userId, "테스트유저", null, LocalDate.of(2000, 1, 1),
        location, 3, "https://example.com/image.jpg"
    );
    given(profileService.getProfile(userId, userId)).willReturn(response);

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .with(authentication(authenticationFor(userId))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("존재하지 않는 프로필을 조회하면 400를 반환한다")
  void getProfileWithNonExistentProfileReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(profileService.getProfile(userId, userId)).willThrow(new ProfileNotFoundException(userId));

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .with(authentication(authenticationFor(userId))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("본인이 아닌 사용자의 프로필을 조회하면 403을 반환한다")
  void getProfileWithDifferentUserReturns403() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    given(profileService.getProfile(userId, otherUserId))
        .willThrow(new ProfileAccessDeniedException());

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .with(authentication(authenticationFor(otherUserId))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("본인 프로필 수정 요청이 성공하면 200을 반환한다")
  void updateProfileReturns200() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    LocationDto location = new LocationDto(37.5, 127.0, 60, 127, List.of("서울특별시", "강남구", "역삼동"));
    ProfileDto response = new ProfileDto(
        userId, "새이름", Gender.MALE, LocalDate.of(1995, 5, 5),
        location, 3, null
    );
    given(profileService.updateProfile(any(UUID.class), any(UUID.class), any(ProfileUpdateRequest.class), any()))
        .willReturn(response);

    MockPart requestPart = new MockPart(
        "request",
        "{\"name\":\"새이름\",\"gender\":\"MALE\",\"birthDate\":\"1995-05-05\",\"location\":{\"latitude\":37.5,\"longitude\":127.0},\"temperatureSensitivity\":3}".getBytes(StandardCharsets.UTF_8)
    );
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // when & then
    mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
            .part(requestPart)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(authentication(authenticationFor(userId)))
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("존재하지 않는 프로필을 수정하면 400을 반환한다")
  void updateProfileWithNonExistentProfileReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(profileService.updateProfile(any(UUID.class), any(UUID.class), any(ProfileUpdateRequest.class), any()))
        .willThrow(new ProfileNotFoundException(userId));

    MockPart requestPart = new MockPart(
        "request",
        "{\"name\":\"새이름\"}".getBytes(StandardCharsets.UTF_8)
    );
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // when & then
    mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
            .part(requestPart)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(authentication(authenticationFor(userId)))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("본인이 아닌 사용자의 프로필을 수정하면 403을 반환한다")
  void updateProfileWithDifferentUserReturns403() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    given(profileService.updateProfile(any(UUID.class), any(UUID.class), any(ProfileUpdateRequest.class), any()))
        .willThrow(new ProfileAccessDeniedException());

    MockPart requestPart = new MockPart(
        "request",
        "{\"name\":\"새이름\"}".getBytes(StandardCharsets.UTF_8)
    );
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // when & then
    mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
            .part(requestPart)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(authentication(authenticationFor(otherUserId)))
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("위치 정보 중 latitude만 전달하면 400을 반환한다")
  void updateProfileWithOnlyLatitudeReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    MockPart requestPart = new MockPart(
        "request",
        "{\"name\":\"새이름\",\"gender\":\"MALE\",\"birthDate\":\"1995-05-05\",\"location\":{\"latitude\":37.5},\"temperatureSensitivity\":3}".getBytes(StandardCharsets.UTF_8)
    );
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // when & then
    mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
            .part(requestPart)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(authentication(authenticationFor(userId)))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("위치 정보 중 longitude만 전달하면 400을 반환한다")
  void updateProfileWithOnlyLongitudeReturns400() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    MockPart requestPart = new MockPart(
        "request",
        "{\"name\":\"새이름\",\"gender\":\"MALE\",\"birthDate\":\"1995-05-05\",\"location\":{\"longitude\":127.0},\"temperatureSensitivity\":3}".getBytes(StandardCharsets.UTF_8)
    );
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // when & then
    mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
            .part(requestPart)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(authentication(authenticationFor(userId)))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("이미지와 함께 프로필 수정 요청이 성공하면 서비스로 이미지가 전달된다")
  void updateProfileWithImageReturns200() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    LocationDto location = new LocationDto(null, null, null, null, List.of());
    ProfileDto response = new ProfileDto(
        userId, "이미지테스트", null, null, location, null,
        "https://example.com/uploaded-image.jpg"
    );
    given(profileService.updateProfile(any(UUID.class), any(UUID.class), any(ProfileUpdateRequest.class), any()))
        .willReturn(response);

    MockPart requestPart = new MockPart(
        "request",
        "{\"name\":\"이미지테스트\"}".getBytes(StandardCharsets.UTF_8)
    );
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    MockMultipartFile imagePart = new MockMultipartFile(
        "image", "test.png", "image/png", "dummy-content".getBytes()
    );

    // when & then
    mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
            .file(imagePart)
            .part(requestPart)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(authentication(authenticationFor(userId)))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/uploaded-image.jpg"));
  }
}