package com.buddygo.buddygoserver.domain.review.repository;

import com.buddygo.buddygoserver.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 받은 평가 수 (프로필·호스트 카드의 '후기 n') */
    long countByTargetId(Long targetId);
}
