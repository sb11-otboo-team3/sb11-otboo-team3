package com.otboo.global.error;

import java.util.Map;

public record ErrorResponse(
        String exceptionName,
        String message,
        Map<String, String> details
) {
}
