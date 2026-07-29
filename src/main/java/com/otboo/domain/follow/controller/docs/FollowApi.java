package com.otboo.domain.follow.controller.docs;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Tag(name = "팔로우 관리", description = "팔로우 관련 API")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FollowApi {
}