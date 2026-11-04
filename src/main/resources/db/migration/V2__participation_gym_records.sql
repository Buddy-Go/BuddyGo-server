-- 신청 시점의 신청자 헬스 수준 스냅샷. 호스트의 참가자 관리 화면은 이 값을 보여주고,
-- 이후 신청자가 프로필을 바꿔도 그대로다. 입력하지 않은(미설정) 부위는 행이 없다.
CREATE TABLE participation_gym_records
(
    participation_id BIGINT      NOT NULL REFERENCES participations (id),
    body_part        VARCHAR(20) NOT NULL,
    record_value     NUMERIC,
    arm_level        VARCHAR(20),
    PRIMARY KEY (participation_id, body_part)
);
