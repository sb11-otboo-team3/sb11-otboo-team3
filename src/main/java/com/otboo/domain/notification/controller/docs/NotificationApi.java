package com.otboo.domain.notification.controller.docs;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Tag(name = "알림", description = "알림 API")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotificationApi {
}