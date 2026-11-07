-- 버디 신청은 수락하면 buddies 행이 기록이 되고, 보낸 사람의 취소는 남길 이유가 없어 행을 지운다.
-- 거절(REJECTED)은 그대로 남긴다. 알림의 buddy_request_id 는 V1 부터 ON DELETE SET NULL 이다.
DELETE FROM buddy_requests WHERE status IN ('ACCEPTED', 'CANCELED');
