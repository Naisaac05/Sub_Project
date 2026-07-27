-- payments.matching_id 를 NULL 허용으로 되돌린다 (엔티티 정의와 DB 스키마의 드리프트 교정).
--
-- 증상: 결제 생성이 항상 `Column 'matching_id' cannot be null` 로 실패한다.
--   - PaymentService.createPayment 는 Payment.builder() 에 matchingId 를 넣지 않는다.
--   - DataInitializer 도 시드 결제 5건을 matchingId(null) 로 저장하므로, 빈 DB 부팅 시 시드에서 먼저 터진다.
--
-- 원인: 엔티티는 처음부터 nullable 을 의도했다.
--   Payment.java — "매칭 연결 (결제 후 추천→선택 완료 시 세팅됨, 처음에는 null)" + linkMatching(Long)
--   그런데 DB 컬럼은 과거 ddl-auto 가 NOT NULL 로 만들어놓은 뒤, `update` 모드가 제약을 완화하지
--   않아 그대로 굳었다. Hibernate 의 validate 는 컬럼 존재/타입만 보고 nullability 는 검사하지 않아
--   이 드리프트를 잡아주지 못한다 — 그래서 명시적 마이그레이션이 필요하다.
--
-- 방향: 엔티티에 nullable = false 를 붙이는 게 아니라, DB 를 엔티티 의도에 맞춘다.
-- 참고: matching_id 의 UNIQUE 제약은 유지된다. MySQL 은 유니크 인덱스에 NULL 중복을 허용한다.

ALTER TABLE payments
    MODIFY COLUMN matching_id BIGINT NULL;
