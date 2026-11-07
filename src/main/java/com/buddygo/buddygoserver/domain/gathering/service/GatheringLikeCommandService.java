package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLike;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLikeId;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringLikeRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GatheringLikeCommandService {

    private final GatheringRepository gatheringRepository;
    private final GatheringLikeRepository gatheringLikeRepository;
    private final UserRepository userRepository;

    /** 모임 행을 잠가 중복 찜 요청과 모임 삭제가 동시에 처리되지 않게 한다. */
    public void like(Long userId, Long gatheringId) {
        Gathering gathering = lockGathering(gatheringId);
        GatheringLikeId id = new GatheringLikeId(userId, gatheringId);
        if (gatheringLikeRepository.existsById(id)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        gatheringLikeRepository.save(GatheringLike.builder()
                .id(id).user(user).gathering(gathering).build());
    }

    /** 반복 해제도 성공한다. 다른 사용자의 찜에는 영향을 주지 않는다. */
    public void unlike(Long userId, Long gatheringId) {
        lockGathering(gatheringId);
        gatheringLikeRepository.deleteById(new GatheringLikeId(userId, gatheringId));
    }

    private Gathering lockGathering(Long gatheringId) {
        return gatheringRepository.findActiveByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
    }
}
