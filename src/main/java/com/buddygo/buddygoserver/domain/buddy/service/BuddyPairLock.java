package com.buddygo.buddygoserver.domain.buddy.service;

import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 두 사람 사이의 버디 쓰기(신청·취소·수락·거절·끊기)는 모두 두 사용자 행을 먼저 잠근다.
 * A→B, B→A 신청이 동시에 와도 (sender, receiver) 쌍이 달라 부분 유니크 인덱스로는 막을 수 없어서다.
 * 교착을 피하려고 항상 id 오름차순으로 잠그고, 신청·버디 행은 잠근 뒤에 읽는다.
 */
@Component
@RequiredArgsConstructor
class BuddyPairLock {

    private final UserRepository userRepository;

    /** 잠근 두 사용자. 없는 사용자면 U001 */
    record Pair(User me, User other) {
    }

    Pair lock(Long meId, Long otherId) {
        User first = lockUser(Math.min(meId, otherId));
        User second = lockUser(Math.max(meId, otherId));
        return first.getId().equals(meId) ? new Pair(first, second) : new Pair(second, first);
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
    }
}
