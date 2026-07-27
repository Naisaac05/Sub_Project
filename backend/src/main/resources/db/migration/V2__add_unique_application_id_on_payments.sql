-- 신청서 1건당 결제 1건 불변식을 DB 레벨에서 강제한다.
--
-- 배경: PaymentService.createPayment 는 existsByApplicationId 로 중복을 선검사한 뒤 save 하는
-- check-then-act(TOCTOU) 구조라, 동시 요청이 둘 다 선검사를 통과하면 결제가 2건 생성될 수 있었다.
-- 이 제약이 그 경쟁의 최후 방어다 (두 번째 INSERT 를 DB 가 물리적으로 거부).
--
-- 주의: ddl-auto: update 는 "기존 테이블의 기존 컬럼"에 유니크 제약을 추가하지 않는다.
-- 엔티티에 @Column(unique = true) 를 써도 반영되지 않아(2026-07-27 실측 확인),
-- 이렇게 명시적 마이그레이션으로 적용해야 한다.
--
-- 선행 조건: application_id 에 중복 행이 없어야 한다. 남아 있으면 이 마이그레이션은 실패하고
-- 앱 기동이 중단된다(의도된 동작 — 조용히 넘어가지 않는다). 아래로 확인 후 정리한다.
--   SELECT application_id, COUNT(*) c FROM payments GROUP BY application_id HAVING c > 1;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_application_id UNIQUE (application_id);
