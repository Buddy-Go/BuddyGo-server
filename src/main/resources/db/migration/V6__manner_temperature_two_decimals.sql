-- 매너온도는 100 에 가까울수록 오르는 폭(0 에 가까울수록 내리는 폭)이 줄어든다.
-- 높은 온도에서의 작은 변화도 쌓이도록 소수 2자리로 저장한다. API 는 소수 1자리로 반올림해 준다.
ALTER TABLE users ALTER COLUMN manner_temperature TYPE NUMERIC(5, 2);
ALTER TABLE manner_temperature_logs ALTER COLUMN delta TYPE NUMERIC(5, 2);
