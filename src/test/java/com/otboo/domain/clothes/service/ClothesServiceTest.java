package com.otboo.domain.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.domain.clothes.dto.response.ClothesListResponse;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.domain.clothes.exception.ClothesNotFoundException;
import com.otboo.domain.clothes.exception.DuplicateClothesAttributeException;
import com.otboo.domain.clothes.exception.InvalidClothesAttributeValueException;
import com.otboo.domain.clothes.exception.InvalidClothesCursorException;
import com.otboo.domain.clothes.mapper.ClothesMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.infrastructure.storage.FileStorage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import com.otboo.global.infrastructure.storage.event.FileReplacementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private ClothesAttributeRepository clothesAttributeRepository;

    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeSelectableValueRepository selectableValueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClothesMapper clothesMapper;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ClothesService service;


    @BeforeEach
    void setUp() {
        ClothesWriteTransactionalService writeService = new ClothesWriteTransactionalService(
                clothesRepository, clothesAttributeRepository, definitionRepository,
                selectableValueRepository, userRepository, clothesMapper, eventPublisher
        );
        service = new ClothesService(
                clothesRepository, clothesAttributeRepository, selectableValueRepository,
                clothesMapper, writeService, fileStorage
        );
    }

    @Test
    void 정상_등록하면_의상이_생성된다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ClothesCreateRequest request = new ClothesCreateRequest(userId, "티셔츠", ClothesType.TOP, List.of());

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
        given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(UUID.randomUUID(), userId, "티셔츠", null, ClothesType.TOP, List.of()));

        //when
        ClothesResponse response = service.create(userId, request, null);

        //then
        assertThat(response.name()).isEqualTo("티셔츠");
    }

    @Test
    void 본인이_아닌_ownerId로_등록하면_예외가_발생한다() {
        //given
        UUID currentUserId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(otherOwnerId, "티셔츠", ClothesType.TOP, List.of());

        //when & then
        assertThatThrownBy(() -> service.create(currentUserId, request, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 존재하지_않는_사용자로_등록하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(userId, "티셔츠", ClothesType.TOP, List.of());

        given(userRepository.findById(userId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> service.create(userId, request, null))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 중복된_definitionId로_등록하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        UUID definitionId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(
                userId, "티셔츠", ClothesType.TOP,
                List.of(
                        new ClothesAttributeRequest(definitionId, "빨강"),
                        new ClothesAttributeRequest(definitionId, "파랑")
                )
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
        given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> invocation.getArgument(0));

        //when & then
        assertThatThrownBy(() -> service.create(userId, request, null))
                .isInstanceOf(DuplicateClothesAttributeException.class);
    }

    @Test
    void 존재하지_않는_속성_정의로_등록하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        UUID definitionId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(
                userId, "티셔츠", ClothesType.TOP, List.of(new ClothesAttributeRequest(definitionId, "빨강"))
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
        given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(definitionRepository.findAllById(List.of(definitionId))).willReturn(List.of());
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of()))
                .willReturn(List.of());

        //when & then
        assertThatThrownBy(() -> service.create(userId, request, null))
                .isInstanceOf(ClothesAttributeDefinitionNotFoundException.class);
    }

    @Test
    void 활성_선택값에_없는_값으로_등록하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        UUID definitionId = UUID.randomUUID();
        ReflectionTestUtils.setField(definition, "id", definitionId);

        ClothesCreateRequest request = new ClothesCreateRequest(
                userId, "티셔츠", ClothesType.TOP, List.of(new ClothesAttributeRequest(definitionId, "보라"))
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
        given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(definitionRepository.findAllById(List.of(definitionId))).willReturn(List.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of());

        //when & then
        assertThatThrownBy(() -> service.create(userId, request, null))
                .isInstanceOf(InvalidClothesAttributeValueException.class);
    }

    @Test
    void 정상_등록_시_유효한_속성값이_저장된다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        UUID definitionId = UUID.randomUUID();
        ReflectionTestUtils.setField(definition, "id", definitionId);
        AttributeSelectableValue selectableValue = new AttributeSelectableValue(definition, "빨강", 0);

        ClothesCreateRequest request = new ClothesCreateRequest(
                userId, "티셔츠", ClothesType.TOP, List.of(new ClothesAttributeRequest(definitionId, "빨강"))
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
        given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(definitionRepository.findAllById(List.of(definitionId))).willReturn(List.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of(selectableValue));
        given(clothesAttributeRepository.save(any(ClothesAttribute.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(UUID.randomUUID(), userId, "티셔츠", null, ClothesType.TOP, List.of()));

        //when
        service.create(userId, request, null);

        //then
        verify(clothesAttributeRepository).save(any(ClothesAttribute.class));
    }

    @Test
    void 정상_수정하면_이름과_속성이_갱신된다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "기존이름", null, ClothesType.TOP);

        ClothesUpdateRequest request = new ClothesUpdateRequest("새이름", ClothesType.BOTTOM, List.of(), null);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(clothesId, userId, "새이름", null, ClothesType.BOTTOM, List.of()));

        //when
        ClothesResponse response = service.update(userId, clothesId, request, null);

        //then
        assertThat(response.name()).isEqualTo("새이름");
        assertThat(clothes.getName()).isEqualTo("새이름");
        assertThat(clothes.getType()).isEqualTo(ClothesType.BOTTOM);
        verify(clothesAttributeRepository).deleteByClothes(clothes);
    }

    @Test
    void 존재하지_않는_의상을_수정하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        ClothesUpdateRequest request = new ClothesUpdateRequest("새이름", ClothesType.TOP, List.of(), null);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> service.update(userId, clothesId, request, null))
                .isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    void 본인_소유가_아닌_의상을_수정하면_예외가_발생한다() {
        //given
        UUID currentUserId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Clothes clothes = new Clothes(owner, "기존이름", null, ClothesType.TOP);

        ClothesUpdateRequest request = new ClothesUpdateRequest("새이름", ClothesType.TOP, List.of(), null);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

        //when & then
        assertThatThrownBy(() -> service.update(currentUserId, clothesId, request, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 수정_시_비활성_선택값을_요청하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "기존이름", null, ClothesType.TOP);

        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        UUID definitionId = UUID.randomUUID();
        ReflectionTestUtils.setField(definition, "id", definitionId);

        ClothesUpdateRequest request = new ClothesUpdateRequest(
                "새이름", ClothesType.TOP, List.of(new ClothesAttributeRequest(definitionId, "보라")), null
        );

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
        given(definitionRepository.findAllById(List.of(definitionId))).willReturn(List.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of());

        //when & then
        assertThatThrownBy(() -> service.update(userId, clothesId, request, null))
                .isInstanceOf(InvalidClothesAttributeValueException.class);
    }

    @Test
    void 정상_삭제하면_소프트_삭제된다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "옷", null, ClothesType.TOP);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

        //when
        service.delete(userId, clothesId);

        //then
        assertThat(clothes.getDeletedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_의상을_삭제하면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();

        given(clothesRepository.findById(clothesId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> service.delete(userId, clothesId))
                .isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    void 본인_소유가_아닌_의상을_삭제하면_예외가_발생한다() {
        //given
        UUID currentUserId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Clothes clothes = new Clothes(owner, "옷", null, ClothesType.TOP);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

        //when & then
        assertThatThrownBy(() -> service.delete(currentUserId, clothesId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 정상_목록_조회하면_결과를_반환한다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "옷", null, ClothesType.TOP);

        given(clothesRepository.findClothesList(userId, null, null, null, 21))
                .willReturn(List.of(clothes));
        given(clothesAttributeRepository.findByClothesIn(List.of(clothes))).willReturn(List.of());
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of()))
                .willReturn(List.of());
        given(clothesRepository.countClothes(userId, null)).willReturn(1L);
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(UUID.randomUUID(), userId, "옷", null, ClothesType.TOP, List.of()));

        //when
        ClothesListResponse response = service.getList(userId, userId, null, null, null, 20);

        //then
        assertThat(response.data()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 본인_옷장이_아니면_목록_조회_시_예외가_발생한다() {
        //given
        UUID currentUserId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        //when & then
        assertThatThrownBy(() -> service.getList(currentUserId, otherOwnerId, null, null, null, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cursor만_있고_idAfter가_없으면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();

        //when & then
        assertThatThrownBy(() -> service.getList(userId, userId, null, "2026-01-01T00:00:00Z", null, 20))
                .isInstanceOf(InvalidClothesCursorException.class);
    }

    @Test
    void 잘못된_형식의_cursor면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();

        //when & then
        assertThatThrownBy(() -> service.getList(userId, userId, null, "invalid-cursor", UUID.randomUUID(), 20))
                .isInstanceOf(InvalidClothesCursorException.class);
    }

    @Test
    void 다음_페이지가_있으면_nextCursor를_반환한다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);

        Clothes clothes1 = new Clothes(owner, "옷1", null, ClothesType.TOP);
        ReflectionTestUtils.setField(clothes1, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(clothes1, "createdAt", Instant.now());
        Clothes clothes2 = new Clothes(owner, "옷2", null, ClothesType.TOP);

        given(clothesRepository.findClothesList(userId, null, null, null, 2))
                .willReturn(List.of(clothes1, clothes2));
        given(clothesAttributeRepository.findByClothesIn(List.of(clothes1))).willReturn(List.of());
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of()))
                .willReturn(List.of());
        given(clothesRepository.countClothes(userId, null)).willReturn(5L);
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(UUID.randomUUID(), userId, "옷1", null, ClothesType.TOP, List.of()));

        //when
        ClothesListResponse response = service.getList(userId, userId, null, null, null, 1);

        //then
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextIdAfter()).isEqualTo(clothes1.getId());
        assertThat(response.nextCursor()).isEqualTo(clothes1.getCreatedAt().toString());
    }

    @Test
    void 이미지와_함께_등록하면_업로드_후_imageKey가_저장된다() {
        //given
        UUID userId = UUID.randomUUID();
        User owner = User.create("test@Otbbo.id", "테스트", "encoded-password");
        ClothesCreateRequest request = new ClothesCreateRequest(userId, "티셔츠", ClothesType.TOP, List.of());
        MultipartFile image = new MockMultipartFile("image", "shirt.png", "image/png", "dummy".getBytes());
        StoredFile storedFile = new StoredFile("clothes/" + userId + "/key.png", "image/png", 5L);

        given(fileStorage.upload(StorageDirectory.CLOTHES, userId, image)).willReturn(storedFile);
        given(userRepository.findById(userId)).willReturn(Optional.of(owner));
        given(clothesRepository.save(any(Clothes.class))).willAnswer(
                invocation -> invocation.getArgument(0));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(UUID.randomUUID(), userId,
                        "티셔츠", null, ClothesType.TOP, List.of()));

        //when
        service.create(userId, request, image);

        //then
        ArgumentCaptor<Clothes> captor = ArgumentCaptor.forClass(Clothes.class);
        verify(clothesRepository).save(captor.capture());

        assertThat(captor.getValue().getImageKey()).isEqualTo("clothes/" + userId + "/key.png");
    }

    @Test
    void 새_이미지로_수정하면_교체되고_이벤트가_발행된다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "기존이름", "clothes/" + userId + "/old.png", ClothesType.TOP);

        ClothesUpdateRequest request = new ClothesUpdateRequest("새이름", ClothesType.TOP, List.of(), null);
        MultipartFile image = new MockMultipartFile("image","new.png", "image/png", "dummy".getBytes());
        StoredFile storedFile = new StoredFile("clothes/" + userId + "/new.png", "image/png", 5L);

        given(fileStorage.upload(StorageDirectory.CLOTHES, userId, image)).willReturn(storedFile);
        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(clothesId, userId,
                        "새이름", null, ClothesType.TOP, List.of()));

        //when
        service.update(userId, clothesId, request, image);

        //then
        assertThat(clothes.getImageKey()).isEqualTo("clothes/" + userId + "/new.png");
        verify(eventPublisher).publishEvent(
                new FileReplacementEvent("clothes/" + userId +
                        "/old.png","clothes/" + userId + "/new.png")
        );
    }

    @Test
    void deleteImage가_true면_이미지가_삭제되고_이벤트가_발행된다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트",
                "encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "기존이름", "clothes/" +
                userId + "/old.png", ClothesType.TOP);

        ClothesUpdateRequest request = new
                ClothesUpdateRequest("새이름", ClothesType.TOP, List.of(), true);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(clothesId, userId,
                        "새이름", null, ClothesType.TOP, List.of()));

        //when
        service.update(userId, clothesId, request, null);

        //then
        assertThat(clothes.getImageKey()).isNull();
        verify(eventPublisher).publishEvent(
                new FileReplacementEvent("clothes/" + userId +"/old.png", null)
        );
    }

    @Test
    void 이미지_변경_없이_수정하면_이벤트가_발행되지_않는다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트","encoded-password");
        ReflectionTestUtils.setField(owner, "id", userId);
        Clothes clothes = new Clothes(owner, "기존이름", "clothes/" + userId + "/old.png", ClothesType.TOP);

        ClothesUpdateRequest request = new
                ClothesUpdateRequest("새이름", ClothesType.TOP, List.of(), null);

        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
        given(clothesMapper.toResponse(any(), any(), any()))
                .willReturn(new ClothesResponse(clothesId, userId,
                        "새이름", null, ClothesType.TOP, List.of()));

        //when
        service.update(userId, clothesId, request, null);

        //then
        assertThat(clothes.getImageKey()).isEqualTo("clothes/" + userId + "/old.png");
        verify(eventPublisher, never()).publishEvent(any());
    }

}