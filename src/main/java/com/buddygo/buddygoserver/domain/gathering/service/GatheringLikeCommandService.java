package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLikeId;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringLikeRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** 찜은 모임 상태와 무관해 모임 행을 잠그지 않는다. 중복 찜은 ON CONFLICT 로 건너뛴다. */
@Service
@RequiredArgsConstructor
@Transactional
public class GatheringLikeCommandService {

    private final GatheringRepository gatheringRepository;
    private final GatheringLikeRepository gatheringLikeRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** 이미 찜한 모임도 성공한다. */
    public void like(Long userId, Long gatheringId) {
        requireActiveGathering(gatheringId);
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorType.NOT_FOUND);
        }
        gatheringLikeRepository.insertIgnoringDuplicate(userId, gatheringId, Instant.now(clock));
    }

    /** 반복 해제도 성공한다. 다른 사용자의 찜에는 영향을 주지 않는다. */
    public void unlike(Long userId, Long gatheringId) {
        requireActiveGathering(gatheringId);
        gatheringLikeRepository.deleteById(new GatheringLikeId(userId, gatheringId));
    }

    private void requireActiveGathering(Long gatheringId) {
        if (!gatheringRepository.existsActiveById(gatheringId)) {
            throw new CustomException(GatheringErrorType.NOT_FOUND);
        }
    }
}
