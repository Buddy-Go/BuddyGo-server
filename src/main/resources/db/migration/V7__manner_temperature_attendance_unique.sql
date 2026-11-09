-- 노쇼·지각 페널티는 한 모임에서 사람마다 사유별로 한 번만 들어간다.
-- 코드도 한 번만 부르지만(노쇼는 새로 기록된 노쇼만, 지각은 체크인 1회), 이력은 이 인덱스에 ON CONFLICT DO NOTHING 으로 넣고
-- 들어갔을 때만 온도를 바꾼다. 경로가 늘어 두 번 불려도 이중 감점이 생기지 않는다.
CREATE UNIQUE INDEX uk_manner_temperature_logs_attendance
    ON manner_temperature_logs (user_id, gathering_id, reason)
    WHERE reason IN ('NO_SHOW', 'LATE');
