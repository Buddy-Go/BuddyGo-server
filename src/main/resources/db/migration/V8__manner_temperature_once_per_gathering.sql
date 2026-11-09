-- 모임당 한 번뿐인 매너온도 사유를 넓힌다: 노쇼·지각에 더해 데드라인 후 나가기(방폭)와 호스트 취소.
-- 데드라인 전 나가기는 다시 들어와 또 나갈 수 있어 모임당 여러 번 생기므로 넣지 않는다.
-- 이력은 이 인덱스에 ON CONFLICT DO NOTHING 으로 넣고 들어갔을 때만 온도를 바꾼다(MannerTemperatureLogRepository).
DROP INDEX uk_manner_temperature_logs_attendance;

CREATE UNIQUE INDEX uk_manner_temperature_logs_once_per_gathering
    ON manner_temperature_logs (user_id, gathering_id, reason)
    WHERE reason IN ('NO_SHOW', 'LATE', 'LEFT_AFTER_DEADLINE', 'HOST_CANCELED_RECRUITING', 'HOST_CANCELED_CLOSED');
