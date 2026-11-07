package com.buddygo.buddygoserver.domain.user.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.user.dto.response.MyActivitiesResponse;
import com.buddygo.buddygoserver.domain.user.dto.response.MyActivityResponse;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.MyActivityRepository;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyActivityQueryService {

    private final MyActivityRepository myActivityRepository;
    private final UserRepository userRepository;
    private final ParticipationRepository participationRepository;
    private final CheckInRepository checkInRepository;
    private final Clock clock;

    public MyActivitiesResponse getActivities(Long userId, String role, String period, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorType.NOT_FOUND);
        }
        Instant now = Instant.now(clock);
        boolean upcoming = period.equals("upcoming");
        Sort.Direction direction = upcoming ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<Participation> activities = myActivityRepository.findMyActivities(userId, role.equals("host"), upcoming,
                now, PageRequest.of(page, size, Sort.by(direction, "gathering.startAt", "id")));
        if (activities.isEmpty()) {
            return new MyActivitiesResponse(List.of(), page, size, activities.getTotalElements(), activities.getTotalPages());
        }
        Map<Long, Long> counts = participationRepository.countConfirmedByGatheringIds(activities.stream()
                        .map(p -> p.getGathering().getId()).toList()).stream()
                .collect(Collectors.toMap(ParticipationRepository.ConfirmedCount::getGatheringId,
                        ParticipationRepository.ConfirmedCount::getCount));
        Map<Long, Attendance> attendance = checkInRepository.findAllById(activities.stream()
                        .map(Participation::getId).toList()).stream()
                .collect(Collectors.toMap(CheckIn::getId, CheckIn::getAttendance));
        List<MyActivityResponse> content = activities.stream()
                .map(p -> MyActivityResponse.of(p, attendance.get(p.getId()),
                        counts.getOrDefault(p.getGathering().getId(), 0L), now)).toList();
        return new MyActivitiesResponse(content, page, size, activities.getTotalElements(), activities.getTotalPages());
    }
}
