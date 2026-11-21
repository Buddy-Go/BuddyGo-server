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
    /** 원댓글만. 삭제 표시된 원댓글도 답글이 남아 있으므로 포함한다. */
    @EntityGraph(attributePaths = "author")
    Slice<NoticeComment> findByNoticeIdAndParentIsNull(Long noticeId, Pageable pageable);

    /** 원댓글들의 답글을 작성순으로 한 번에 읽는다. */
    @EntityGraph(attributePaths = "author")
    @Query("select c from NoticeComment c where c.parent.id in :parentIds order by c.createdAt, c.id")
    List<NoticeComment> findRepliesOf(Collection<Long> parentIds);

    Optional<NoticeComment> findByIdAndNoticeId(Long id, Long noticeId);

    @Query("select count(c) > 0 from NoticeComment c where c.parent.id = :parentId")
    boolean hasReplies(Long parentId);

    @Query("select distinct c.author.id from NoticeComment c where c.notice.id = :noticeId and c.deletedAt is null")
    List<Long> findCommenterIds(Long noticeId);

    /** 지우지 않은 댓글 수(답글 포함) */
    @Query("select count(c) from NoticeComment c where c.notice.id = :noticeId and c.deletedAt is null")
    long countVisibleByNoticeId(Long noticeId);

    interface CommentCount {
        Long getNoticeId();

        long getCount();
    }

    /** 공지마다 지우지 않은 댓글 수(답글 포함). 댓글이 없는 공지는 결과에 없다. */
    @Query("select c.notice.id as noticeId, count(c) as count from NoticeComment c "
            + "where c.notice.id in :noticeIds and c.deletedAt is null group by c.notice.id")
    List<CommentCount> countByNoticeIds(Collection<Long> noticeIds);
}
