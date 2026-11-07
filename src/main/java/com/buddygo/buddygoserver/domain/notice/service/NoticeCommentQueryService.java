package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.response.CommentListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeCommentQueryService {
    private final NoticeAccess access;
    private final NoticeCommentRepository commentRepository;

    public CommentListResponse comments(Long userId, Long gatheringId, Long noticeId, int page, int size) {
        access.read(userId, gatheringId);
        access.notice(gatheringId, noticeId);
        var comments = commentRepository.findByNoticeId(noticeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id")));
        return new CommentListResponse(comments.map(CommentResponse::from).getContent(), page, size, comments.hasNext());
    }
}
