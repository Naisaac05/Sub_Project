-- =======================================================================
-- [3차 AI 스트리밍 하드닝] UNIQUE 제약 추가 전 중복 데이터 정합성 검사 및 복구 가이드
-- =======================================================================
--
-- [중요 경고]
-- 현재 로컬/샌드박스 격리 환경에서는 실제 MySQL 운영/개발 데이터베이스로의 물리적 접속이 불가능합니다.
-- 따라서, 실제 프로덕션 데이터 내의 중복 데이터 실존 확률 및 데이터 상태는 "확인 불가"입니다.
-- 실제 운영 및 개발 DB에 UNIQUE 제약을 적용하기 전에, 반드시 본 문서의 단계를 순서대로 실행해 주세요.
--
-- =======================================================================

-- -----------------------------------------------------------------------
-- [1단계] 필수 정합성 조회 SQL (운영/개발 DB 사전 확인 필수)
-- -----------------------------------------------------------------------
-- 중복 멱등키가 테이블에 이미 적재되어 있는지 집계하여 검사합니다.
-- 결과 행(Row)이 1개라도 반환되면 중복 데이터가 존재하는 것이며, DDL 적용 전 복구가 필요합니다.

SELECT session_id, client_request_id, COUNT(*)
FROM ai_review_messages
WHERE client_request_id IS NOT NULL
GROUP BY session_id, client_request_id
HAVING COUNT(*) > 1;


-- -----------------------------------------------------------------------
-- [2단계] DRY-RUN SELECT (NULL 처리 대상 ID 사전 확인용)
-- -----------------------------------------------------------------------
-- 1단계에서 중복이 발견되었을 때, 실제 업데이트를 가하기 전에 
-- '2번째 이후 중복 레코드(ID가 더 큰 행)'의 ID와 상세 정보를 미리 확인(Dry-run)하는 조회 쿼리입니다.
-- 이 조회 결과로 나온 ID들이 3단계 업데이트 대상이 됩니다.

SELECT m.id, m.session_id, m.client_request_id, m.role, m.content, m.created_at
FROM ai_review_messages m
JOIN (
    SELECT id, 
           ROW_NUMBER() OVER (PARTITION BY session_id, client_request_id ORDER BY id ASC) as rn
    FROM ai_review_messages
    WHERE client_request_id IS NOT NULL
) dup ON m.id = dup.id
WHERE dup.rn > 1
ORDER BY m.session_id, m.client_request_id, m.id;


-- -----------------------------------------------------------------------
-- [3단계] UPDATE TO NULL (전략 B: 중복 데이터 격리 및 보존)
-- -----------------------------------------------------------------------
-- 중복이 발견될 경우, 데이터를 삭제하지 않고 중복된 2번째 이후 행의 
-- client_request_id 필드만 NULL로 변경하여 데이터 유실 없이 무결성을 확보합니다.
-- 레거시 client_request_id가 없는 요청들과의 하위 호환성을 유지하면서 UNIQUE 제약을 걸기 위한 안전장치입니다.

UPDATE ai_review_messages m
JOIN (
    SELECT id, 
           ROW_NUMBER() OVER (PARTITION BY session_id, client_request_id ORDER BY id ASC) as rn
    FROM ai_review_messages
    WHERE client_request_id IS NOT NULL
) dup ON m.id = dup.id
SET m.client_request_id = NULL
WHERE dup.rn > 1;


-- -----------------------------------------------------------------------
-- [4단계] ALTER DDL: 복합 UNIQUE 제약조건 추가
-- -----------------------------------------------------------------------
-- 중복 데이터 정합성 보정이 완료된 후, 테이블에 session_id와 client_request_id 복합 UNIQUE 제약을 안전하게 적용합니다.

ALTER TABLE ai_review_messages 
ADD CONSTRAINT uk_session_client_request 
UNIQUE (session_id, client_request_id);
