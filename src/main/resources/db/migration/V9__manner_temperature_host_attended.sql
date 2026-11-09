-- 호스트 출석 보너스(HOST_ATTENDED)도 모임당 한 번뿐이므로 부분 유니크 인덱스 조건에 넣는다.
-- 인덱스 조건은 MannerTemperatureReason 의 oncePerGathering 과 MannerTemperatureLogRepository 의 ON CONFLICT 조건과 같아야 한다
-- (OncePerGatheringReasonSyncTest).
DROP INDEX uk_manner_temperature_logs_once_per_gathering;

CREATE UNIQUE INDEX uk_manner_temperature_logs_once_per_gathering
    ON manner_temperature_logs (user_id, gathering_id, reason)
    WHERE reason IN ('NO_SHOW', 'LATE', 'LEFT_AFTER_DEADLINE', 'HOST_CANCELED_RECRUITING', 'HOST_CANCELED_CLOSED',
                     'HOST_ATTENDED');
