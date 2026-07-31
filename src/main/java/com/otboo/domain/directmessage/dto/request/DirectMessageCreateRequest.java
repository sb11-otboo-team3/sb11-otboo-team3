package com.otboo.domain.directmessage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DirectMessageCreateRequest(

    @NotNull(message = "수신자 ID는 필수입니다.")
    UUID receiverId,

    @NotNull(message = "발신자 ID는 필수입니다.")
    UUID senderId,

    @NotBlank(message = "메시지 내용은 필수입니다.")
    @Size(max = 1000, message = "메세지는 1000자 이하로 입력해주세요.")
    String content
) {

}