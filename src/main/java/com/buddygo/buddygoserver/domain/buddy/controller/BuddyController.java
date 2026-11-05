package com.buddygo.buddygoserver.domain.buddy.controller;

import com.buddygo.buddygoserver.domain.buddy.api.BuddyApi;
import com.buddygo.buddygoserver.domain.buddy.dto.response.ActivityResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyResponse;
import com.buddygo.buddygoserver.domain.buddy.service.BuddyQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BuddyController implements BuddyApi {

    private final BuddyQueryService buddyQueryService;

    @Override
    public ResponseEntity<SuccessResponse<List<BuddyResponse>>> getBuddies(Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(buddyQueryService.getBuddies(userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<List<ActivityResponse>>> getActivities(Long loginUserId, Long targetId) {
        return ResponseEntity.ok(SuccessResponse.from(buddyQueryService.getActivities(loginUserId, targetId)));
    }
}
