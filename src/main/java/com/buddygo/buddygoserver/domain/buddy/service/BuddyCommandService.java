package com.buddygo.buddygoserver.domain.buddy.service;

import com.buddygo.buddygoserver.domain.buddy.dto.request.BuddyNotificationRequest;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyNotificationResponse;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyId;
import com.buddygo.buddygoserver.domain.buddy.exception.BuddyErrorType;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BuddyCommandService {

    private final BuddyRepository buddyRepository;
    private final BuddyPairLock buddyPairLock;

    /** 버디 끊기. 두 방향 행을 함께 지운다. 끊은 뒤에도 함께 운동한 사람이면 다시 신청할 수 있다. */
    public void delete(Long userId, Long buddyId) {
        buddyPairLock.lock(userId, buddyId);
        if (buddyRepository.deleteBetween(userId, buddyId) == 0) {
            throw new CustomException(BuddyErrorType.BUDDY_NOT_FOUND);
        }
    }

    /** 이 버디의 모임 개설·신청을 나에게 알릴지. 내 쪽 행만 바뀐다. */
    public BuddyNotificationResponse changeNotification(Long userId, Long buddyId, BuddyNotificationRequest request) {
        Buddy buddy = buddyRepository.findById(new BuddyId(userId, buddyId))
                .orElseThrow(() -> new CustomException(BuddyErrorType.BUDDY_NOT_FOUND));
        buddy.changeNotification(request.gatheringCreated(), request.gatheringApplied());
        return BuddyNotificationResponse.from(buddy);
    }
}
