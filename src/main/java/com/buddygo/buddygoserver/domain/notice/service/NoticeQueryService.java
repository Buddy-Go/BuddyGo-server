package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeResponse;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {
    private final NoticeAccess access;
    private final NoticeRepository noticeRepository;

    public NoticeListResponse list(Long userId, Long gatheringId, int page, int size) {
        access.read(userId, gatheringId);
        var notices = noticeRepository.findByGatheringId(gatheringId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new NoticeListResponse(notices.map(NoticeResponse::from).getContent(), page, size, notices.hasNext());
    }

    public NoticeResponse detail(Long userId, Long gatheringId, Long noticeId) {
        access.read(userId, gatheringId);
        return NoticeResponse.from(access.notice(gatheringId, noticeId));
    }

}
