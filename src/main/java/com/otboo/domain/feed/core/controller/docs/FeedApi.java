package com.otboo.domain.feed.core.controller.docs;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Tag(name = "피드 관리", description = "피드 관련 API")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeedApi {
}