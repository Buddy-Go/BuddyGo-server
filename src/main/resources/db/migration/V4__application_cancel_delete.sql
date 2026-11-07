-- 신청자가 대기 중인 신청을 취소하면 참여 행을 남기지 않고 지운다.
-- 취소에는 매너온도·알림 영향이 없고 남긴 행을 읽는 곳도 없다. 신청 시점 스냅샷(participation_gym_records)은
-- V3 의 FK CASCADE 로 함께 지워진다. 이미 CANCELED 로 남은 행을 지워야 enum 에서 값을 뺄 수 있다.
DELETE FROM participations WHERE status = 'CANCELED';
