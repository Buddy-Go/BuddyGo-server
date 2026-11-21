package com.buddygo.buddygoserver.domain.notice.entity;

import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "notice_comments")
public class NoticeComment extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Notice notice;

    /** 답글이면 원댓글. 답글은 1단계만 있어서 부모는 항상 원댓글이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private NoticeComment parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** 삭제 표시된 원댓글이면 null */
    @Column(length = 200)
    private String content;

    /** 답글이 남아 있어 행만 남긴 원댓글의 삭제 시각 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static NoticeComment replyTo(NoticeComment parent, User author, String content) {
        return builder().notice(parent.getNotice()).parent(parent).author(author).content(content).build();
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 답글이 달린 원댓글을 지울 때. 행은 남기고 내용만 비운다. */
    public void markDeleted(Instant now) {
        this.deletedAt = now;
        this.content = null;
    }
}
