package com.buddygo.buddygoserver.domain.notice.entity;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import java.time.Instant;
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
@Table(name = "notices")
public class Notice extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 200)
    private String content;

    /** '수정됨' 표시용 */
    @Column(name = "edited_at")
    private Instant editedAt;

    public void edit(String title, String content, Instant now) {
        if (title != null) { this.title = title.strip(); }
        if (content != null) { this.content = content.strip(); }
        this.editedAt = now;
    }
}
