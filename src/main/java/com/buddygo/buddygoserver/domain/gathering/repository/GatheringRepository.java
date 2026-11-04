package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/** 삭제된 모임(deleted_at)은 없는 모임으로 본다. */
public interface GatheringRepository extends JpaRepository<Gathering, Long> {

    @Query("""
            select g from Gathering g
            join fetch g.host
            join fetch g.place
            where g.id = :id and g.deletedAt is null""")
    Optional<Gathering> findActiveWithHostAndPlace(Long id);

    /**
     * 확정 인원을 읽고 그 결과로 수정·삭제·취소하므로 모임 행을 잠근다.
     * 신청 수락도 같은 행을 잠가야 확정 인원 판단이 어긋나지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gathering g where g.id = :id and g.deletedAt is null")
    Optional<Gathering> findActiveByIdForUpdate(Long id);
}
