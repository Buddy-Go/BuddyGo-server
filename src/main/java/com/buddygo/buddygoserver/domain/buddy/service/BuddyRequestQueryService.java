package com.buddygo.buddygoserver.domain.buddy.service;

import com.buddygo.buddygoserver.domain.buddy.dto.response.ReceivedBuddyRequestResponse;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRequestRepository;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuddyRequestQueryService {

    private final BuddyRequestRepository buddyRequestRepository;
    private final CheckInRepository checkInRepository;

    /** 받은 대기 중 신청(최근 순). 보낸 사람의 닉네임·매너온도·참여 횟수를 담는다. */
    public List<ReceivedBuddyRequestResponse> getReceived(Long userId) {
        List<BuddyRequest> requests = buddyRequestRepository.findPendingReceivedWithSender(userId);
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> participationCounts = checkInRepository.countAttendedByUserIds(
                        requests.stream().map(r -> r.getSender().getId()).toList()).stream()
                .collect(Collectors.toMap(CheckInRepository.AttendedCount::getUserId,
                        CheckInRepository.AttendedCount::getCount));
        return requests.stream()
                .map(r -> ReceivedBuddyRequestResponse.of(r, participationCounts.getOrDefault(r.getSender().getId(), 0L)))
                .toList();
    }
}
