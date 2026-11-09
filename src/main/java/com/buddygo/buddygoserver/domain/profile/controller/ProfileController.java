package com.buddygo.buddygoserver.domain.profile.controller;

import com.buddygo.buddygoserver.domain.profile.api.ProfileApi;
import com.buddygo.buddygoserver.domain.profile.dto.response.MyProfileResponse;
import com.buddygo.buddygoserver.domain.profile.dto.response.UserProfileResponse;
import com.buddygo.buddygoserver.domain.profile.service.ProfileQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProfileController implements ProfileApi {

    private final ProfileQueryService profileQueryService;

    @Override
    public ResponseEntity<SuccessResponse<MyProfileResponse>> getMe(Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(profileQueryService.getMe(userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<UserProfileResponse>> getUser(Long viewerId, Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(profileQueryService.getUser(viewerId, userId)));
    }
}
