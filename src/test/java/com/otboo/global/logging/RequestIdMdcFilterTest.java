package com.otboo.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdMdcFilterTest {

    private final RequestIdMdcFilter filter = new RequestIdMdcFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Request ID 헤더가 없으면 새로운 UUID를 생성하고 응답 헤더와 MDC에 등록한다")
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        // when
        filter.doFilter(request, response, (req, res) ->
                requestIdInChain.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        // then
        String requestId = requestIdInChain.get();

        assertThat(requestId).isNotBlank();
        assertThatCodeIsUuid(requestId);
        assertThat(response.getHeader(RequestIdMdcFilter.REQUEST_ID_HEADER))
                .isEqualTo(requestId);
    }

    @Test
    @DisplayName("유효한 UUID 형식의 Request ID 헤더가 있으면 해당 값을 재사용한다")
    void reusesValidRequestIdHeader() throws Exception {
        // given
        String requestId = UUID.randomUUID().toString();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdMdcFilter.REQUEST_ID_HEADER, requestId);

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        // when
        filter.doFilter(request, response, (req, res) ->
                requestIdInChain.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        // then
        assertThat(requestIdInChain.get()).isEqualTo(requestId);
        assertThat(response.getHeader(RequestIdMdcFilter.REQUEST_ID_HEADER))
                .isEqualTo(requestId);
    }

    @Test
    @DisplayName("유효하지 않은 Request ID 헤더가 들어오면 새로운 UUID를 생성한다")
    void replacesInvalidRequestIdHeader() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                RequestIdMdcFilter.REQUEST_ID_HEADER,
                "invalid-request-id"
        );

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        // when
        filter.doFilter(request, response, (req, res) ->
                requestIdInChain.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        // then
        String generatedRequestId = requestIdInChain.get();

        assertThat(generatedRequestId)
                .isNotBlank()
                .isNotEqualTo("invalid-request-id");

        assertThatCodeIsUuid(generatedRequestId);
    }

    @Test
    @DisplayName("요청 처리가 끝나면 requestId MDC를 제거한다")
    void clearsRequestIdAfterRequest() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                        .isNotNull()
        );

        // then
        assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                .isNull();
    }

    @Test
    @DisplayName("요청 처리 중 예외가 발생해도 requestId MDC를 제거한다")
    void clearsRequestIdWhenExceptionOccurs() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new ServletException("test exception");
                })
        ).isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                .isNull();
    }

    @Test
    @DisplayName("ASYNC 디스패치에서도 최초 요청과 동일한 requestId를 사용한다")
    void keepsSameRequestIdOnAsyncDispatch() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        AtomicReference<String> firstRequestId = new AtomicReference<>();

        filter.doFilter(request, firstResponse, (req, res) ->
                firstRequestId.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        request.setDispatcherType(DispatcherType.ASYNC);

        MockHttpServletResponse asyncResponse = new MockHttpServletResponse();
        AtomicReference<String> asyncRequestId = new AtomicReference<>();

        // when
        filter.doFilter(request, asyncResponse, (req, res) ->
                asyncRequestId.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        // then
        assertThat(asyncRequestId.get())
                .isEqualTo(firstRequestId.get());

        assertThat(asyncResponse.getHeader(RequestIdMdcFilter.REQUEST_ID_HEADER))
                .isEqualTo(firstRequestId.get());
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value).toString())
                .isEqualTo(value);
    }

    @Test
    @DisplayName("연속된 요청은 서로 다른 Request ID를 사용하고 이전 MDC가 남지 않는다")
    void doesNotLeakRequestIdBetweenRequests() throws Exception {
        // given
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        AtomicReference<String> firstRequestId = new AtomicReference<>();

        filter.doFilter(firstRequest, firstResponse, (req, res) ->
                firstRequestId.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                .isNull();

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        AtomicReference<String> secondRequestId = new AtomicReference<>();

        // when
        filter.doFilter(secondRequest, secondResponse, (req, res) ->
                secondRequestId.set(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
        );

        // then
        assertThat(secondRequestId.get())
                .isNotEqualTo(firstRequestId.get());

        assertThat(MDC.get(RequestIdMdcFilter.MDC_REQUEST_ID_KEY))
                .isNull();
    }
}