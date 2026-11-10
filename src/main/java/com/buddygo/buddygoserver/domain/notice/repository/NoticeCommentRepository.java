package com.buddygo.buddygoserver.domain.notice.repository;

import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {
    @EntityGraph(attributePaths = "author")
    Slice<NoticeComment> findByNoticeId(Long noticeId, Pageable pageable);

    Optional<NoticeComment> findByIdAndNoticeId(Long id, Long noticeId);

    @Query("select distinct c.author.id from NoticeComment c where c.notice.id = :noticeId")
    List<Long> findCommenterIds(Long noticeId);

    long countByNoticeId(Long noticeId);

    interface CommentCount {
        Long getNoticeId();

        long getCount();
    }

    /** 공지마다 댓글 수. 댓글이 없는 공지는 결과에 없다. */
    @Query("select c.notice.id as noticeId, count(c) as count from NoticeComment c "
            + "where c.notice.id in :noticeIds group by c.notice.id")
    List<CommentCount> countByNoticeIds(Collection<Long> noticeIds);
}
