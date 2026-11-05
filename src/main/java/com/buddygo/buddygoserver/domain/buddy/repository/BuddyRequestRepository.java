package com.buddygo.buddygoserver.domain.buddy.repository;

import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BuddyRequestRepository extends JpaRepository<BuddyRequest, Long> {

    /** 신청을 엔티티로 읽기 전에 두 사람만 알아내 잠금 순서를 정한다(먼저 읽으면 잠근 뒤에도 옛 값이 남는다). */
    interface Parties {
        Long getSenderId();

        Long getReceiverId();
    }

    @Query("select r.sender.id as senderId, r.receiver.id as receiverId from BuddyRequest r where r.id = :id")
    Optional<Parties> findPartiesById(Long id);

    /** sender → receiver 로 대기 중인 신청. 부분 유니크 인덱스로 하나뿐이다. */
    @Query("""
            select r from BuddyRequest r
            where r.sender.id = :senderId and r.receiver.id = :receiverId
              and r.status = com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus.PENDING""")
    Optional<BuddyRequest> findPending(Long senderId, Long receiverId);

    @Query("""
            select r from BuddyRequest r
            join fetch r.sender
            where r.receiver.id = :receiverId
              and r.status = com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus.PENDING
            order by r.createdAt desc, r.id desc""")
    List<BuddyRequest> findPendingReceivedWithSender(Long receiverId);

    /** userId 와 상대들 사이에 오가는 대기 중인 신청(어느 방향이든) */
    @Query("""
            select r from BuddyRequest r
            where r.status = com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus.PENDING
              and ((r.sender.id = :userId and r.receiver.id in :otherIds)
                   or (r.receiver.id = :userId and r.sender.id in :otherIds))""")
    List<BuddyRequest> findPendingBetween(Long userId, Collection<Long> otherIds);
}
