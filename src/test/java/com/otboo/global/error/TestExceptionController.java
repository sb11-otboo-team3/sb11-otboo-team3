package com.otboo.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestExceptionController {

    @GetMapping("/domain-exception")
    public void throwDomainException() {
        throw new TestNotFoundException();
    }

    @GetMapping("/unexpected-exception")
    public void throwUnexpectedException() {
        throw new IllegalStateException("테스트 서버 오류");
    }

    static class TestNotFoundException extends OtbooException {

        TestNotFoundException() {
            super(
                    HttpStatus.NOT_FOUND,
                    "테스트 리소스를 찾을 수 없습니다."
            );
        }
    }
}