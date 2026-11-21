package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.response.CommentListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeCommentQueryService {
    private final NoticeAccess access;
    private final NoticeCommentRepository commentRepository;

    /** 원댓글 기준으로 페이지를 나누고, 각 원댓글에 답글을 모두 담는다(모임 인원이 적어 답글 수가 작다). */
    public CommentListResponse comments(Long userId, Long gatheringId, Long noticeId, int page, int size) {
        Long hostId = access.read(userId, gatheringId).getHost().getId();
        access.notice(gatheringId, noticeId);
        var roots = commentRepository.findByNoticeIdAndParentIsNull(noticeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id")));
        List<Long> rootIds = roots.map(NoticeComment::getId).getContent();
        Map<Long, List<CommentResponse>> replies = rootIds.isEmpty() ? Map.of()
                : commentRepository.findRepliesOf(rootIds).stream()
                .collect(Collectors.groupingBy(r -> r.getParent().getId(),
                        Collectors.mapping(r -> CommentResponse.from(r, hostId), Collectors.toList())));
        return new CommentListResponse(roots.map(c -> CommentResponse.from(c, hostId,
                replies.getOrDefault(c.getId(), List.of()))).getContent(), page, size, roots.hasNext());
    }
}
