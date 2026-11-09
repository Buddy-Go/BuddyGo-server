package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {

    /** 개설 횟수('개설 n회'): 이 상태(진행중·종료 = 실제로 진행된) 모임 수 */
    long countByHostIdAndStatusIn(Long hostId, Collection<GatheringStatus> statuses);

    @Query("""
            select g from Gathering g
            join fetch g.host
            join fetch g.place
            where g.id = :id""")
    Optional<Gathering> findWithHostAndPlace(Long id);

    /** 탐색 카드용. 순서는 보장하지 않는다. */
    @Query("""
            select g from Gathering g
            join fetch g.host
            join fetch g.place
            where g.id in :ids""")
    List<Gathering> findAllWithHostAndPlace(Collection<Long> ids);

    /**
     * 모임 행을 FOR KEY SHARE 로 잡고 있으면 true, 없으면 false.
     * 모임 삭제(DELETE 의 FOR UPDATE)만 막고 호스트 작업의 잠금(FOR NO KEY UPDATE)과는 충돌하지 않는다.
     * 삭제가 진행 중이면 커밋을 기다렸다가 사라진 행을 보고 false 가 된다.
     */
    @Query(nativeQuery = true, value = "SELECT EXISTS (SELECT 1 FROM gatherings WHERE id = :id FOR KEY SHARE)")
    boolean lockForKeyShare(Long id);

    /**
     * 확정 인원을 읽고 그 결과로 수정·삭제·취소하므로 모임 행을 잠근다.
     * 신청 수락도 같은 행을 잠가야 확정 인원 판단이 어긋나지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gathering g where g.id = :id")
    Optional<Gathering> findByIdForUpdate(Long id);

    /**
     * 모임 행을 지운다. 참여·찜·헬스 기준·공지는 DB FK CASCADE 로 함께 지워지므로, 영속성 컨텍스트에 남은
     * 자식 엔티티가 지워진 모임을 가리킨 채 flush 되지 않도록 벌크 삭제 후 컨텍스트를 비운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Gathering g where g.id = :id")
    void deleteWithChildren(Long id);

    /** 상태 전이 스케줄러의 대상 후보: 이 상태이고 시작 시각이 startAt 이전(포함)인 모임 */
    @Query("""
            select g.id from Gathering g
            where g.status = :status and g.startAt <= :startAt
            order by g.startAt, g.id""")
    List<Long> findIdsByStatusStartingBy(GatheringStatus status, Instant startAt);

    /** 상태 전이 스케줄러의 대상 후보: 이 상태이고 종료 시각이 endAt 이전(포함)인 모임 */
    @Query("""
            select g.id from Gathering g
            where g.status = :status and g.endAt <= :endAt
            order by g.endAt, g.id""")
    List<Long> findIdsByStatusEndingBy(GatheringStatus status, Instant endAt);
}
