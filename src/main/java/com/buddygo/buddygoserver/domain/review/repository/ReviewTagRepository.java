package com.buddygo.buddygoserver.domain.review.repository;

import com.buddygo.buddygoserver.domain.review.entity.ReviewTag;
import com.buddygo.buddygoserver.domain.review.entity.ReviewTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ReviewTagRepository extends JpaRepository<ReviewTag, ReviewTagId> {

    interface ReviewTagRow {
        Long getReviewId();

        String getTag();
    }

    /** 평가 목록 한 페이지의 태그를 한 번에 읽는다. */
    @Query("select t.id.reviewId as reviewId, t.id.tag as tag from ReviewTag t where t.id.reviewId in :reviewIds")
    List<ReviewTagRow> findByReviewIds(Collection<Long> reviewIds);

    interface TagCount {
        String getTag();

        long getCount();
    }

    /** 받은 태그별 개수. 아쉬움 평가의 태그는 공개하지 않으므로 세지 않는다. */
    @Query("""
            select t.id.tag as tag, count(t) as count
            from ReviewTag t join t.review r
            where r.target.id = :targetId
              and r.rating <> com.buddygo.buddygoserver.domain.review.enums.ReviewRating.BAD
            group by t.id.tag""")
    List<TagCount> countPublicByTargetId(Long targetId);
}
