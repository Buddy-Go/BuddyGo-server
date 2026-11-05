package com.buddygo.buddygoserver.domain.buddy.repository;

import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** 버디 관계는 두 방향 행이 함께 생기고 함께 지워지므로, 한쪽 행만 보고 판단해도 된다. */
public interface BuddyRepository extends JpaRepository<Buddy, BuddyId> {

    default boolean areBuddies(Long userId, Long otherId) {
        return existsById(new BuddyId(userId, otherId));
    }

    @Query("""
            select b from Buddy b
            join fetch b.buddy
            where b.id.userId = :userId
            order by b.createdAt desc""")
    List<Buddy> findAllWithBuddy(Long userId);
}
