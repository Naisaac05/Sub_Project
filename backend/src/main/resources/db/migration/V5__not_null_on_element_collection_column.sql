-- @ElementCollection 조인 테이블의 원소 컬럼을 NOT NULL 로 조인다.
--
-- Application.rejectedMentors 는 Set<Long> 인데, 원소가 저장되는 mentor_id 컬럼이 NULL 을 허용했다.
-- NULL 행이 생기면 컬렉션을 읽을 때 Set 안에 null 원소가 섞여 들어와, 이후 비교·언박싱에서
-- 조용히 오동작하거나 NPE 가 난다. 조인 테이블의 원소 컬럼에 NULL 은 의미가 없다.
--
-- 관련: backend/src/main/java/com/devmatch/entity/Application.java 의 rejectedMentors
--
-- 참고: 이 교정은 V4(역방향 드리프트)와 성격이 같지만, V4 는 이미 적용된 마이그레이션이라
-- 수정할 수 없어(checksum 검증) 새 버전으로 추가한다. Flyway 는 forward-only 다.
--
-- 현재 이 테이블은 0행이라 백필이 필요 없지만, 데이터가 있는 환경을 위해 방어적으로 정리한다.

DELETE FROM application_rejected_mentors WHERE mentor_id IS NULL;

ALTER TABLE application_rejected_mentors
    MODIFY COLUMN mentor_id BIGINT NOT NULL;
