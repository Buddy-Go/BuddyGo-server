package com.buddygo.buddygoserver.domain.user.repository;

import com.buddygo.buddygoserver.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    /**
     * 여러 사용자 행을 id 오름차순으로 잠근다. PostgreSQL 은 정렬한 순서대로 행을 잠그므로, 여러 사람을 잠그는
     * 다른 경로(BuddyPairLock 등)와 같은 순서가 되어 교착이 생기지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id")
    List<User> findAllByIdsForUpdate(Collection<Long> ids);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
