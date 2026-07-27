-- 역방향 nullability 드리프트 교정: DB 는 NULL 을 허용하는데 엔티티는 값이 항상 있다고 가정하는 컬럼들.
--
-- 배경: 이 컬럼들은 엔티티에 @Builder.Default 로 기본값이 선언돼 있다(예: swapCount = 0).
-- 그런데 컬럼이 나중에 추가되면서 기존 행에는 NULL 이 남았고, ddl-auto 는 NOT NULL 로 조이지 못했다.
-- 그 결과 "엔티티는 항상 값이 있다고 보는데 실제로는 NULL 인 행"이 공존하게 됐다.
--
-- 실제 피해:
--  1) matchings.swap_count — Matching.swap() 이 `this.swapCount++` 를 하므로 NULL 이면 NPE.
--     MentorSwapService:54 에서 활성 매칭에 호출되는데, 개발 DB 의 ACCEPTED 매칭 1건이 NULL 이었다.
--  2) payments.* — 호출부가 `getMonthsBundled() != null ? ... : 1` 같은 방어 코드로 우회하고 있었다
--     (CurriculumService:105,117). 스키마가 정합해지면 그런 우회가 필요 없어진다.
--
-- 방향: 엔티티의 기본값이 곧 의도이므로 DB 를 그 의도에 맞춘다. 기존 NULL 은 같은 기본값으로 백필한다.
--
-- 제외 대상(의도적으로 nullable 인 것들 — 건드리지 않는다):
--  - matchings.trial_end_date : 체험 시작 시에만 세팅되며 isInTrialPeriod() 가 null 을 명시적으로 검사한다.
--  - payments.course_type     : 엔티티에 @Builder.Default 가 없어 선택 값으로 취급된다.

-- 1) 기존 NULL 백필 (엔티티 기본값과 동일한 값으로)
UPDATE matchings SET swap_count        = 0 WHERE swap_count        IS NULL;
UPDATE payments  SET months_bundled    = 1 WHERE months_bundled    IS NULL;
UPDATE payments  SET renewal_count     = 0 WHERE renewal_count     IS NULL;
UPDATE payments  SET discount_applied  = 0 WHERE discount_applied  IS NULL;
UPDATE payments  SET installment_months = 0 WHERE installment_months IS NULL;

-- 2) NOT NULL + DEFAULT 로 조이기 (앞으로 NULL 이 다시 생기지 않게)
ALTER TABLE matchings MODIFY COLUMN swap_count         INT NOT NULL DEFAULT 0;
ALTER TABLE payments  MODIFY COLUMN months_bundled     INT NOT NULL DEFAULT 1;
ALTER TABLE payments  MODIFY COLUMN renewal_count      INT NOT NULL DEFAULT 0;
ALTER TABLE payments  MODIFY COLUMN discount_applied   INT NOT NULL DEFAULT 0;
ALTER TABLE payments  MODIFY COLUMN installment_months INT NOT NULL DEFAULT 0;
