package com.otboo.global.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestValidationController {

    @PostMapping("/validation")
    public void validate(
            @Valid @RequestBody TestRequest request
    ) {
    }

    @PostMapping("/validation/fallback")
    public void validateFallback(
            @Valid @RequestBody FallbackTestRequest request
    ) {
    }

    @GetMapping("/query")
    public void validateQuery(
            @RequestParam String keyword
    ) {
    }

    @GetMapping("/users/{userId}")
    public void validatePathVariable(
            @PathVariable("userId") UUID userId
    ) {
    }

    public record TestRequest(
            @NotBlank(message = "이름은 필수입니다.")
            String name
    ) {
    }

    public record FallbackTestRequest(
            @NotBlank(message = " ")
            String value
    ) {

    }
}