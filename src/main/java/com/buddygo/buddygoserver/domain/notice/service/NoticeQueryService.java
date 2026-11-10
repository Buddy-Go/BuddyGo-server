package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeResponse;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository.CommentCount;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {
    private final NoticeAccess access;
    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository commentRepository;

    public NoticeListResponse list(Long userId, Long gatheringId, int page, int size) {
        access.read(userId, gatheringId);
        var notices = noticeRepository.findByGatheringId(gatheringId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        // 댓글 수는 페이지의 공지 ID 로 한 번에 센다
        Map<Long, Long> commentCounts = commentRepository.countByNoticeIds(
                        notices.map(Notice::getId).getContent()).stream()
                .collect(Collectors.toMap(CommentCount::getNoticeId, CommentCount::getCount));
        return new NoticeListResponse(
                notices.map(n -> NoticeResponse.from(n, commentCounts.getOrDefault(n.getId(), 0L))).getContent(),
                page, size, notices.hasNext());
    }

    public NoticeResponse detail(Long userId, Long gatheringId, Long noticeId) {
        access.read(userId, gatheringId);
        var notice = access.notice(gatheringId, noticeId);
        return NoticeResponse.from(notice, commentRepository.countByNoticeId(noticeId));
    }

}
