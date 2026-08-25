package com.otboo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.otboo.domain.notification.dto.response.NotificationDto;
import com.otboo.domain.notification.dto.response.NotificationDtoCursorResponse;
import com.otboo.domain.notification.entity.Notification;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.exception.InvalidNotificationCursorException;
import com.otboo.domain.notification.exception.NotificationForbiddenException;
import com.otboo.domain.notification.exception.NotificationNotFoundException;
import com.otboo.domain.notification.exception.NotificationUserNotFoundException;
import com.otboo.domain.notification.repository.NotificationRepository;
import com.otboo.domain.notification.sse.SseEmitterRegistry;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("알림 목록 조회 성공 테스트")
    void getNotifications_success() {
        UUID receiverId = UUID.randomUUID();
        User receiver = createUser(receiverId);

        Notification notification = createNotification(UUID.randomUUID(), receiver);

        given(notificationRepository.findNotifications(receiverId, null, null, 21))
                .willReturn(List.of(notification));
        given(notificationRepository.countNotifications(receiverId)).willReturn(1L);

        NotificationDtoCursorResponse result =
                notificationService.getNotifications(null, null, 20, receiverId);

        assertThat(result.data()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(1L);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("cursor와 idAfter 둘 중 하나만 있는 경우 예외 테스트")
    void getNotifications_invalidCursor_throwsException() {
        UUID receiverId = UUID.randomUUID();

        assertThatThrownBy(() ->
                notificationService.getNotifications("2026-08-03T10:00:00Z", null, 20, receiverId)
        ).isInstanceOf(InvalidNotificationCursorException.class);

        verify(notificationRepository, never()).findNotifications(any(), any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("알림 삭제 성공 테스트")
    void deleteNotification_success() {
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User receiver = createUser(receiverId);
        Notification notification = createNotification(notificationId, receiver);

        given(notificationRepository.findById(notificationId))
                .willReturn(Optional.of(notification));

        notificationService.deleteNotification(notificationId, receiverId);

        verify(notificationRepository).delete(notification);
    }

    @Test
    @DisplayName("다른 사용자의 알림을 삭제하는 경우 예외 테스트")
    void deleteNotification_forbidden_throwsException() {
        UUID receiverId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User receiver = createUser(receiverId);
        Notification notification = createNotification(notificationId, receiver);

        given(notificationRepository.findById(notificationId))
                .willReturn(Optional.of(notification));

        assertThatThrownBy(() ->
                notificationService.deleteNotification(notificationId, currentUserId)
        ).isInstanceOf(NotificationForbiddenException.class);

        verify(notificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("알림 생성 성공 테스트")
    void createNotification_success() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User receiver = createUser(receiverId);

        given(notificationRepository.findByEventId(eventId))
                .willReturn(Optional.empty());

        given(userRepository.findById(receiverId))
                .willReturn(Optional.of(receiver));

        given(notificationRepository.saveAndFlush(any(Notification.class)))
                .willAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            notification,
                            "id",
                            notificationId
                    );

                    ReflectionTestUtils.setField(
                            notification,
                            "createdAt",
                            Instant.now()
                    );

                    return notification;
                });

        given(sseEmitterRegistry.get(receiverId))
                .willReturn(Optional.empty());

        // when
        NotificationDto result =
                notificationService.createNotification(
                        eventId,
                        receiverId,
                        "새 알림",
                        "알림 내용",
                        NotificationLevel.INFO
                );

        // then
        assertThat(result.id())
                .isEqualTo(notificationId);

        assertThat(result.receiverId())
                .isEqualTo(receiverId);

        assertThat(result.title())
                .isEqualTo("새 알림");

        verify(notificationRepository)
                .saveAndFlush(any(Notification.class));

        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    private User createUser(UUID userId) {
        User user = User.create("user@test.com", "user", "password");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Notification createNotification(UUID notificationId, User receiver) {
        Notification notification = Notification.create(
                receiver,
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO
        );
        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.now());
        return notification;
    }

    @Test
    @DisplayName("동일한 Kafka eventId의 알림이 이미 존재하면 중복 생성하지 않는다")
    void createNotification_duplicateEvent_skipsCreation() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        User receiver = createUser(receiverId);

        Notification existingNotification = Notification.create(
                eventId,
                receiver,
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO
        );

        ReflectionTestUtils.setField(
                existingNotification,
                "id",
                UUID.randomUUID()
        );
        ReflectionTestUtils.setField(
                existingNotification,
                "createdAt",
                Instant.now()
        );

        given(notificationRepository.findByEventId(eventId))
                .willReturn(Optional.of(existingNotification));

        // when
        NotificationDto result = notificationService.createNotification(
                eventId,
                receiverId,
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO
        );

        // then
        assertThat(result.id())
                .isEqualTo(existingNotification.getId());

        verify(userRepository, never()).findById(any());
        verify(notificationRepository, never()).save(any(Notification.class));

        verify(sseEmitterRegistry, never()).get(any());
    }

    @Test
    @DisplayName("알림 목록 조회 실패 - cursor 형식 오류")
    void getNotifications_invalidCursorFormat() {
        UUID receiverId = UUID.randomUUID();

        assertThatThrownBy(() ->
                notificationService.getNotifications(
                        "invalid-cursor",
                        UUID.randomUUID(),
                        20,
                        receiverId
                )
        ).isInstanceOf(InvalidNotificationCursorException.class);

        verify(notificationRepository, never()).findNotifications(any(), any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("알림 삭제 실패 - 알림을 찾을 수 없음")
    void deleteNotification_notFound() {
        UUID notificationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        given(notificationRepository.findById(notificationId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                notificationService.deleteNotification(notificationId, currentUserId)
        ).isInstanceOf(NotificationNotFoundException.class);

        verify(notificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("알림 생성 실패 - 수신자를 찾을 수 없음")
    void createNotification_userNotFound() {
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        given(notificationRepository.findByEventId(eventId))
                .willReturn(Optional.empty());

        given(userRepository.findById(receiverId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                notificationService.createNotification(
                        eventId,
                        receiverId,
                        "알림 제목",
                        "알림 내용",
                        NotificationLevel.INFO
                )
        ).isInstanceOf(NotificationUserNotFoundException.class);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("SSE 구독 성공 - lastEventId 이후 알림 재전송")
    void subscribe_withLastEventId_success() {
        UUID receiverId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();

        User receiver = createUser(receiverId);
        Notification missedNotification =
                createNotification(UUID.randomUUID(), receiver);

        given(notificationRepository.findNotificationsAfter(receiverId, lastEventId, 100))
                .willReturn(List.of(missedNotification));

        SseEmitter emitter = notificationService.subscribe(receiverId, lastEventId);

        assertThat(emitter).isNotNull();

        verify(sseEmitterRegistry).add(receiverId, emitter);
        verify(notificationRepository).findNotificationsAfter(receiverId, lastEventId, 100);
    }

    @Test
    @DisplayName("SSE 구독은 서버 자체 timeout 없이 장시간 연결을 유지한다")
    void subscribe_withoutServerTimeout_success() {
        UUID receiverId = UUID.randomUUID();

        SseEmitter emitter = notificationService.subscribe(receiverId, null);

        assertThat(emitter.getTimeout()).isEqualTo(0L);
    }

    @Test
    @DisplayName("알림 목록 조회 성공 - 다음 페이지가 있는 경우 cursor를 반환한다")
    void getNotifications_hasNext_success() {
        UUID receiverId = UUID.randomUUID();
        User receiver = createUser(receiverId);

        Notification first = createNotification(UUID.randomUUID(), receiver);
        Notification second = createNotification(UUID.randomUUID(), receiver);

        given(notificationRepository.findNotifications(receiverId, null, null, 2))
                .willReturn(List.of(first, second));
        given(notificationRepository.countNotifications(receiverId)).willReturn(2L);

        NotificationDtoCursorResponse result =
                notificationService.getNotifications(null, null, 1, receiverId);

        assertThat(result.data()).hasSize(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(first.getCreatedAt().toString());
        assertThat(result.nextIdAfter()).isEqualTo(first.getId());
        assertThat(result.totalCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("알림 생성 시 트랜잭션이 활성화되어 있으면 커밋 이후 SSE 전송을 등록한다")
    void createNotification_registersAfterCommitSynchronization() {
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User receiver = createUser(receiverId);

        given(notificationRepository.findByEventId(eventId))
                .willReturn(Optional.empty());

        given(userRepository.findById(receiverId)).willReturn(Optional.of(receiver));
        given(notificationRepository.saveAndFlush(any(Notification.class)))
                .willAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            notification,
                            "id",
                            notificationId
                    );

                    ReflectionTestUtils.setField(
                            notification,
                            "createdAt",
                            Instant.now()
                    );

                    return notification;
                });
        given(sseEmitterRegistry.get(receiverId)).willReturn(Optional.empty());

        TransactionSynchronizationManager.initSynchronization();

        try {
            NotificationDto result = notificationService.createNotification(
                    eventId,
                    receiverId,
                    "알림 제목",
                    "알림 내용",
                    NotificationLevel.INFO
            );

            assertThat(result.id()).isEqualTo(notificationId);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertThat(synchronizations).hasSize(1);

            verify(sseEmitterRegistry, never()).get(receiverId);

            synchronizations.get(0).afterCommit();

            verify(sseEmitterRegistry, times(1)).get(receiverId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}