package com.otboo.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        TestValidationController.class,
        TestExceptionController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 요청값_검증에_실패하면_공통_에러응답을_반환한다() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.message")
                        .value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.details.name")
                        .value("이름은 필수입니다."));
    }

    @Test
    void 공통_도메인예외가_발생하면_지정된_HTTP상태와_에러응답을_반환한다() throws Exception {

        mockMvc.perform(get("/test/domain-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName")
                        .value("TestNotFoundException"))
                .andExpect(jsonPath("$.message")
                        .value("테스트 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void 지원하지_않는_HTTP메서드로_요청하면_공통_에러응답을_반환한다() throws Exception {

        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.exceptionName")
                        .value("HttpRequestMethodNotSupportedException"))
                .andExpect(jsonPath("$.message")
                        .value("지원하지 않는 HTTP 메서드입니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void 예상하지_못한_예외가_발생하면_500_공통_에러응답을_반환한다() throws Exception {

        mockMvc.perform(get("/test/unexpected-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.exceptionName")
                        .value("IllegalStateException"))
                .andExpect(jsonPath("$.message")
                        .value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void JSON_형식이_잘못된_요청본문이면_400_공통_에러응답을_반환한다() throws Exception {

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"우진\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("HttpMessageNotReadableException"))
                .andExpect(jsonPath("$.message")
                        .value("요청 본문을 읽을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void 필수_요청파라미터가_누락되면_400_공통_에러응답을_반환한다() throws Exception {

        mockMvc.perform(get("/test/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("MissingServletRequestParameterException"))
                .andExpect(jsonPath("$.message")
                        .value("필수 요청 파라미터가 누락되었습니다."))
                .andExpect(jsonPath("$.details.parameter")
                        .value("keyword"));
    }

    @Test
    void 경로변수의_타입변환에_실패하면_400_공통_에러응답을_반환한다() throws Exception {

        mockMvc.perform(get("/test/users/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("MethodArgumentTypeMismatchException"))
                .andExpect(jsonPath("$.message")
                        .value("요청 값의 타입이 올바르지 않습니다."))
                .andExpect(jsonPath("$.details.parameter")
                        .value("userId"))
                .andExpect(jsonPath("$.details.value")
                        .value("not-a-uuid"));
    }

    @Test
    void 존재하지_않는_API를_요청하면_404_공통_에러응답을_반환한다() throws Exception {

        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName")
                        .value("NoResourceFoundException"))
                .andExpect(jsonPath("$.message")
                        .value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }
}