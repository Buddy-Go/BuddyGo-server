package com.buddygo.buddygoserver.domain.review.repository;

import com.buddygo.buddygoserver.domain.review.entity.ReviewTag;
import com.buddygo.buddygoserver.domain.review.entity.ReviewTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTagRepository extends JpaRepository<ReviewTag, ReviewTagId> {
}
