-- 체크인 열림 알림은 상태 전이 없이 시각만으로 보내므로, 스케줄러가 다시 돌아도 받는 사람마다 모임당 한 번만 남게 한다.
-- 이 인덱스에 ON CONFLICT DO NOTHING 으로 넣는다(NotificationRepository.insertOncePerGatheringIgnoringDuplicate).
CREATE UNIQUE INDEX uk_notifications_once_per_gathering
    ON notifications (receiver_id, gathering_id, type)
    WHERE type IN ('CHECK_IN_OPENED');
