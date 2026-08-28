package com.otboo.domain.follow.dto.response;

import com.otboo.domain.user.dto.UserSummary;
import java.util.UUID;

public record FollowDto(
    UUID id,
    UserSummary followee,
    UserSummary follower
) {

}