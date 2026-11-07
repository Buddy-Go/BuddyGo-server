package com.buddygo.buddygoserver.domain.review.entity;

import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "review_tags")
public class ReviewTag {

    @EmbeddedId
    private ReviewTagId id;

    @MapsId("reviewId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id")
    private Review review;

    public static ReviewTag of(Review review, ReviewTagType tag) {
        return ReviewTag.builder()
                .id(new ReviewTagId(review.getId(), tag.name()))
                .review(review)
                .build();
    }
}
