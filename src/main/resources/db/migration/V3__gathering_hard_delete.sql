-- 모임 삭제를 soft delete(deleted_at)에서 실제 삭제로 바꾼다.
-- 삭제는 확정 참가자(호스트 제외) 0명 · 시작 전에만 가능하므로, 그때 남아 있을 수 있는 행만 함께 지운다.

-- 함께 지울 것: 참여(호스트·대기·거절·취소)와 그 신청 시점 스냅샷, 찜, 헬스 기준, 공지(댓글은 이미 CASCADE)
ALTER TABLE participations
    DROP CONSTRAINT participations_gathering_id_fkey,
    ADD CONSTRAINT participations_gathering_id_fkey
        FOREIGN KEY (gathering_id) REFERENCES gatherings (id) ON DELETE CASCADE;

ALTER TABLE participation_gym_records
    DROP CONSTRAINT participation_gym_records_participation_id_fkey,
    ADD CONSTRAINT participation_gym_records_participation_id_fkey
        FOREIGN KEY (participation_id) REFERENCES participations (id) ON DELETE CASCADE;

ALTER TABLE gathering_likes
    DROP CONSTRAINT gathering_likes_gathering_id_fkey,
    ADD CONSTRAINT gathering_likes_gathering_id_fkey
        FOREIGN KEY (gathering_id) REFERENCES gatherings (id) ON DELETE CASCADE;

ALTER TABLE gathering_gym_parts
    DROP CONSTRAINT gathering_gym_parts_gathering_id_fkey,
    ADD CONSTRAINT gathering_gym_parts_gathering_id_fkey
        FOREIGN KEY (gathering_id) REFERENCES gatherings (id) ON DELETE CASCADE;

ALTER TABLE notices
    DROP CONSTRAINT notices_gathering_id_fkey,
    ADD CONSTRAINT notices_gathering_id_fkey
        FOREIGN KEY (gathering_id) REFERENCES gatherings (id) ON DELETE CASCADE;

-- 알림은 받은 사람의 기록이라 남기고 모임 연결만 끊는다.
ALTER TABLE notifications
    DROP CONSTRAINT notifications_gathering_id_fkey,
    ADD CONSTRAINT notifications_gathering_id_fkey
        FOREIGN KEY (gathering_id) REFERENCES gatherings (id) ON DELETE SET NULL;

-- check_ins · reviews · manner_temperature_logs · gathering_cancellations 는 일부러 그대로(NO ACTION) 둔다.
-- 삭제 조건상 생길 수 없는 이력이라, 버그로 이력이 있는 모임을 지우려 하면 조용히 지우지 않고 실패하게 한다.

-- 이미 soft delete된 모임은 실제로 지운 뒤 컬럼을 없앤다(순서를 바꾸면 되살아난다).
DELETE FROM gatherings WHERE deleted_at IS NOT NULL;

ALTER TABLE gatherings DROP COLUMN deleted_at;
