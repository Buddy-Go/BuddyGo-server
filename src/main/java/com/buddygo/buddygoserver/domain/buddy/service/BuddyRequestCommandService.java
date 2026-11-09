package com.buddygo.buddygoserver.domain.buddy.service;

import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyRequestResponse;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus;
import com.buddygo.buddygoserver.domain.buddy.exception.BuddyErrorType;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRequestRepository;
import com.buddygo.buddygoserver.domain.buddy.service.BuddyPairLock.Pair;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.exception.CustomException;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationMessages;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 버디 신청 → 수락으로 버디가 된다 (서비스 규칙 정의서 10장). */
@Service
@RequiredArgsConstructor
@Transactional
public class BuddyRequestCommandService {

    private final BuddyRequestRepository buddyRequestRepository;
    private final BuddyRepository buddyRepository;
    private final CheckInRepository checkInRepository;
    private final BuddyPairLock buddyPairLock;
    private final NotificationSender notificationSender;
    private final Clock clock;

    /**
     * 함께 운동한 사람(같은 모임에서 둘 다 체크인)에게만 보낼 수 있다.
     * 상대가 나에게 보낸 신청이 대기 중이면 그 신청을 수락한 것으로 보고 바로 버디가 된다.
     * 거절·취소된 뒤에는 다시 보낼 수 있다.
     */
    public BuddyRequestResponse send(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new CustomException(BuddyErrorType.SELF_REQUEST);
        }
        Pair pair = buddyPairLock.lock(senderId, receiverId);
        if (buddyRepository.areBuddies(senderId, receiverId)) {
            throw new CustomException(BuddyErrorType.ALREADY_BUDDY);
        }
        if (!checkInRepository.workedOutTogether(senderId, receiverId)) {
            throw new CustomException(BuddyErrorType.NOT_WORKOUT_PARTNER);
        }
        Optional<BuddyRequest> reverse = buddyRequestRepository.findPending(receiverId, senderId);
        if (reverse.isPresent()) {
            accept(reverse.get(), pair);
            return BuddyRequestResponse.of(reverse.get().getId(), BuddyRequestStatus.ACCEPTED);
        }
        if (buddyRequestRepository.findPending(senderId, receiverId).isPresent()) {
            throw new CustomException(BuddyErrorType.REQUEST_DUPLICATED);
        }
        BuddyRequest request = buddyRequestRepository.saveAndFlush(BuddyRequest.send(pair.me(), pair.other()));
        notificationSender.send(NotificationType.BUDDY_REQUEST_RECEIVED, List.of(receiverId), senderId,
                NotificationTarget.buddyRequest(request.getId()),
                NotificationMessages.buddyRequestReceived(pair.me().getNickname()));
        return BuddyRequestResponse.from(request);
    }

    /** 보낸 사람만, 대기 중일 때만 취소한다. 신청 행은 남기지 않는다. */
    public BuddyRequestResponse cancel(Long userId, Long requestId) {
        BuddyRequest request = lockAndLoad(userId, requestId);
        if (!request.isSentBy(userId)) {
            throw new CustomException(BuddyErrorType.REQUEST_NOT_FOUND);
        }
        requirePending(request);
        buddyRequestRepository.delete(request);
        return BuddyRequestResponse.of(requestId, BuddyRequestStatus.CANCELED);
    }

    /** 받은 사람만. 수락하면 두 방향 버디 행이 생기고, 신청 행은 지운다(버디 행이 기록이다). */
    public BuddyRequestResponse accept(Long userId, Long requestId) {
        BuddyRequest request = lockAndLoad(userId, requestId);
        if (!request.isReceivedBy(userId)) {
            throw new CustomException(BuddyErrorType.REQUEST_NOT_FOUND);
        }
        requirePending(request);
        accept(request, new Pair(request.getReceiver(), request.getSender()));
        return BuddyRequestResponse.of(requestId, BuddyRequestStatus.ACCEPTED);
    }

    /** 받은 사람만. 거절된 사람은 다시 신청할 수 있다. */
    public BuddyRequestResponse reject(Long userId, Long requestId) {
        BuddyRequest request = lockAndLoad(userId, requestId);
        if (!request.isReceivedBy(userId)) {
            throw new CustomException(BuddyErrorType.REQUEST_NOT_FOUND);
        }
        requirePending(request);
        request.reject(Instant.now(clock));
        return BuddyRequestResponse.from(request);
    }

    /**
     * 신청을 수락한다. 신청한 사람에게 수락 알림을 보낸다(맞신청 자동 수락이면 먼저 신청했던 상대에게). 신청 행은 지워지므로
     * 알림은 신청 대신 수락한 사람(actor)으로 이어진다.
     */
    private void accept(BuddyRequest request, Pair pair) {
        Long requesterId = request.getSender().getId();
        User accepter = request.getReceiver();
        buddyRequestRepository.delete(request);
        User me = pair.me();
        User other = pair.other();
        buddyRepository.saveAll(List.of(Buddy.of(me, other), Buddy.of(other, me)));
        buddyRepository.flush();
        notificationSender.send(NotificationType.BUDDY_REQUEST_ACCEPTED, List.of(requesterId), accepter.getId(),
                NotificationTarget.none(), NotificationMessages.buddyRequestAccepted(accepter.getNickname()));
    }

    /**
     * 신청의 두 사람을 먼저 알아내 잠근 뒤 신청을 읽는다. 내가 두 사람 중 하나가 아니면 없는 신청으로 본다.
     * 잠그기 전에 신청 엔티티를 읽지 않으므로 잠근 뒤 읽는 값이 최신이다.
     */
    private BuddyRequest lockAndLoad(Long userId, Long requestId) {
        BuddyRequestRepository.Parties parties = buddyRequestRepository.findPartiesById(requestId)
                .filter(p -> p.getSenderId().equals(userId) || p.getReceiverId().equals(userId))
                .orElseThrow(() -> new CustomException(BuddyErrorType.REQUEST_NOT_FOUND));
        buddyPairLock.lock(parties.getSenderId(), parties.getReceiverId());
        return buddyRequestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(BuddyErrorType.REQUEST_NOT_FOUND));
    }

    private void requirePending(BuddyRequest request) {
        if (!request.isPending()) {
            throw new CustomException(BuddyErrorType.REQUEST_NOT_PENDING);
        }
    }
}
