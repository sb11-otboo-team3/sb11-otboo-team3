package com.otboo.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UserIdMdcFilterTest {

    private final UserIdMdcFilter filter = new UserIdMdcFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 사용자의 UUID를 userId MDC에 등록한다")
    void putsAuthenticatedUserIdIntoMdc() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> userIdInChain = new AtomicReference<>();

        // when
        filter.doFilter(request, response, (req, res) ->
                userIdInChain.set(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
        );

        // then
        assertThat(userIdInChain.get())
                .isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("인증되지 않은 요청에는 userId MDC를 등록하지 않는다")
    void doesNotPutUserIdWhenUnauthenticated() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> userIdInChain = new AtomicReference<>();

        // when
        filter.doFilter(request, response, (req, res) ->
                userIdInChain.set(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
        );

        // then
        assertThat(userIdInChain.get()).isNull();
    }

    @Test
    @DisplayName("요청 처리가 끝나면 userId MDC를 제거한다")
    void clearsUserIdAfterRequest() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
                        .isEqualTo(userId.toString())
        );

        // then
        assertThat(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
                .isNull();
    }

    @Test
    @DisplayName("요청 처리 중 예외가 발생해도 userId MDC를 제거한다")
    void clearsUserIdWhenExceptionOccurs() {
        // given
        UUID userId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new ServletException("test exception");
                })
        ).isInstanceOf(ServletException.class);

        assertThat(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
                .isNull();
    }

    @Test
    @DisplayName("userId를 제거해도 기존 requestId MDC는 유지한다")
    void preservesExistingRequestIdMdc() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        MDC.put(RequestIdMdcFilter.MDC_REQUEST_ID_KEY, requestId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
                    .isEqualTo(userId.toString());

            assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                    .isEqualTo(requestId);
        });

        // then
        assertThat(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
                .isNull();

        assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                .isEqualTo(requestId);
    }

    @Test
    @DisplayName("ASYNC 디스패치에서도 인증된 userId를 MDC에 등록한다")
    void putsUserIdIntoMdcOnAsyncDispatch() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> userIdInChain = new AtomicReference<>();

        // when
        filter.doFilter(request, response, (req, res) ->
                userIdInChain.set(MDC.get(UserIdMdcFilter.MDC_USER_ID_KEY))
        );

        // then
        assertThat(userIdInChain.get())
                .isEqualTo(userId.toString());
    }
}