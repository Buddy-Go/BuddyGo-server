package com.buddygo.buddygoserver.domain.notice.repository;

import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    @EntityGraph(attributePaths = {"gathering", "gathering.host"})
    Slice<Notice> findByGatheringId(Long gatheringId, Pageable pageable);

    @EntityGraph(attributePaths = {"gathering", "gathering.host"})
    Optional<Notice> findByIdAndGatheringId(Long id, Long gatheringId);
}
