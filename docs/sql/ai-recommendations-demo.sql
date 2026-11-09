-- 개발 DB 전용 추천 데모 데이터. ai-recommendations.sql 적용 후 수동 실행한다.
-- 2026-11-06 실제 PostgreSQL 후보를 3단계 엔진/4단계 API로 계산한 결과 스냅샷이다.
-- 점수/이유를 SQL에서 재계산하지 않는다. 최신 추천은 POST API를 사용한다.
-- 기존 member@devmozip.demo 계정과 아래 실제 대상 PK가 있는 동일 개발 데이터셋용이다.
-- 새 회원/프로젝트를 만들지 않으며 기존 데이터를 갱신/삭제하지 않는다.
-- 전용 대화·USER 메시지가 없으면 추가한다. 같은 대화·키는 건너뛰어 재실행 중복을 막는다.
-- 자동 SQL 초기화 파일이 아니다. analysis_level/사용량/Gemini 데이터는 생성하지 않는다.
BEGIN;
SET LOCAL search_path TO public;
SELECT pg_advisory_xact_lock(hashtext('devmozip-ai-demo-v1'));
DO $demo$
DECLARE
    v_owner_id BIGINT;
    v_conversation_id BIGINT;
    v_message_id BIGINT;
    v_request_id BIGINT;
BEGIN
    SELECT id INTO v_owner_id FROM users WHERE email='member@devmozip.demo' AND status='ACTIVE';
    IF v_owner_id IS NULL THEN RAISE EXCEPTION '활성 member@devmozip.demo 계정이 필요합니다.'; END IF;
    IF EXISTS (SELECT 1 FROM unnest(ARRAY[4,5,6,7,8,9]::bigint[]) AS target(id) WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id=target.id)) THEN
        RAISE EXCEPTION '스냅샷의 개발자 대상 PK가 없습니다. 같은 개발 데이터셋에서 실행하세요.';
    END IF;
    IF EXISTS (SELECT 1 FROM unnest(ARRAY[1,2,3,4,5,6,7,8]::bigint[]) AS target(id) WHERE NOT EXISTS (SELECT 1 FROM projects p WHERE p.id=target.id)) THEN
        RAISE EXCEPTION '스냅샷의 프로젝트 대상 PK가 없습니다. 같은 개발 데이터셋에서 실행하세요.';
    END IF;
    IF (SELECT count(*) FROM ai_conversations WHERE user_id=v_owner_id AND title='[DEMO] AI 추천 실행·결과 저장 예제') > 1 THEN
        RAISE EXCEPTION '데모 대화가 여러 개라 대상을 선택할 수 없습니다.';
    END IF;
    SELECT id INTO v_conversation_id FROM ai_conversations WHERE user_id=v_owner_id AND title='[DEMO] AI 추천 실행·결과 저장 예제';
    IF v_conversation_id IS NULL THEN
        INSERT INTO ai_conversations(user_id,title,current_conditions,created_at,updated_at)
        VALUES (v_owner_id,'[DEMO] AI 추천 실행·결과 저장 예제','{"career":"경력 무관","stacks":["React","Python"],"interests":["교육"],"durationMonths":6}'::jsonb,LOCALTIMESTAMP,LOCALTIMESTAMP) RETURNING id INTO v_conversation_id;
    END IF;
    SELECT id INTO v_message_id FROM ai_messages WHERE ai_messages.conversation_id=v_conversation_id AND role='USER' AND content='교육 서비스를 함께 개발할 React와 Python 개발자를 찾고 있어요.' ORDER BY id LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO ai_messages(conversation_id,role,content,created_at)
        VALUES (v_conversation_id,'USER','교육 서비스를 함께 개발할 React와 Python 개발자를 찾고 있어요.',LOCALTIMESTAMP) RETURNING id INTO v_message_id;
    END IF;

    -- 예제 1: 조건 버튼 실행(메시지 NULL)
    v_request_id := NULL;
    INSERT INTO ai_recommendation_requests(ai_conversation_id,ai_message_id,conditions,idempotency_key,status,created_at,completed_at)
    VALUES (v_conversation_id,NULL,'{"career":"경력 무관","stacks":["Java","Spring Boot"],"interests":["생산성"],"durationMonths":3}'::jsonb,'devmozip-demo-ai-v1-button','SUCCESS',LOCALTIMESTAMP,LOCALTIMESTAMP)
    ON CONFLICT (ai_conversation_id,idempotency_key) DO NOTHING RETURNING id INTO v_request_id;
    IF v_request_id IS NOT NULL THEN
        INSERT INTO ai_recommendations(recommended_request_id,recommended_user_id,recommended_project_id,match_score,basic_reason,detailed_reason,rank,created_at) VALUES
            (v_request_id,7,NULL,79.81,'요청 기술 2개 중 2개가 일치합니다.
신뢰 온도가 기본 36.5도보다 높습니다.
관심 분야가 1개 일치합니다(가산점 없음).
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,1,LOCALTIMESTAMP),
            (v_request_id,4,NULL,75.0,'요청 기술 2개 중 2개가 일치합니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,2,LOCALTIMESTAMP),
            (v_request_id,5,NULL,75.0,'요청 기술 2개 중 2개가 일치합니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,3,LOCALTIMESTAMP),
            (v_request_id,9,NULL,3.89,'신뢰 온도가 기본 36.5도보다 높습니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,4,LOCALTIMESTAMP),
            (v_request_id,6,NULL,3.15,'신뢰 온도가 기본 36.5도보다 높습니다.
관심 분야가 1개 일치합니다(가산점 없음).
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,5,LOCALTIMESTAMP),
            (v_request_id,8,NULL,2.04,'신뢰 온도가 기본 36.5도보다 높습니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,6,LOCALTIMESTAMP),
            (v_request_id,NULL,1,91.25,'관심 분야가 1개 일치합니다.
기술 스택이 2개 일치합니다.
모집·진행 상태: IN_PROGRESS
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.',NULL,1,LOCALTIMESTAMP),
            (v_request_id,NULL,5,78.13,'관심 분야가 1개 일치합니다.
기술 스택이 1개 일치합니다.
모집·진행 상태: RECRUITING
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.',NULL,2,LOCALTIMESTAMP),
            (v_request_id,NULL,6,56.25,'기술 스택이 2개 일치합니다.
모집·진행 상태: RECRUITING
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.',NULL,3,LOCALTIMESTAMP),
            (v_request_id,NULL,3,21.88,'기술 스택이 1개 일치합니다.
모집·진행 상태: COMPLETED
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.',NULL,4,LOCALTIMESTAMP),
            (v_request_id,NULL,7,12.5,'모집·진행 상태: RECRUITING
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.
조건과 일치하는 분야·기술·텍스트 정보가 부족합니다.',NULL,5,LOCALTIMESTAMP),
            (v_request_id,NULL,8,12.5,'모집·진행 상태: RECRUITING
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.
조건과 일치하는 분야·기술·텍스트 정보가 부족합니다.',NULL,6,LOCALTIMESTAMP),
            (v_request_id,NULL,2,6.25,'모집·진행 상태: RECRUITMENT_COMPLETED
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.
조건과 일치하는 분야·기술·텍스트 정보가 부족합니다.',NULL,7,LOCALTIMESTAMP),
            (v_request_id,NULL,4,0.0,'모집·진행 상태: COMPLETED
비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.
조건과 일치하는 분야·기술·텍스트 정보가 부족합니다.',NULL,8,LOCALTIMESTAMP);
    END IF;

    -- 예제 2: 동일 대화 USER 메시지 연결 실행
    v_request_id := NULL;
    INSERT INTO ai_recommendation_requests(ai_conversation_id,ai_message_id,conditions,idempotency_key,status,created_at,completed_at)
    VALUES (v_conversation_id,v_message_id,'{"career":"경력 무관","stacks":["React","Python"],"interests":["교육"],"durationMonths":6}'::jsonb,'devmozip-demo-ai-v1-message','SUCCESS',LOCALTIMESTAMP,LOCALTIMESTAMP)
    ON CONFLICT (ai_conversation_id,idempotency_key) DO NOTHING RETURNING id INTO v_request_id;
    IF v_request_id IS NOT NULL THEN
        INSERT INTO ai_recommendations(recommended_request_id,recommended_user_id,recommended_project_id,match_score,basic_reason,detailed_reason,rank,created_at) VALUES
            (v_request_id,7,NULL,42.31,'요청 기술 2개 중 1개가 일치합니다.
신뢰 온도가 기본 36.5도보다 높습니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,1,LOCALTIMESTAMP),
            (v_request_id,9,NULL,41.39,'요청 기술 2개 중 1개가 일치합니다.
신뢰 온도가 기본 36.5도보다 높습니다.
관심 분야가 1개 일치합니다(가산점 없음).
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,2,LOCALTIMESTAMP),
            (v_request_id,6,NULL,40.65,'요청 기술 2개 중 1개가 일치합니다.
신뢰 온도가 기본 36.5도보다 높습니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,3,LOCALTIMESTAMP),
            (v_request_id,8,NULL,39.54,'요청 기술 2개 중 1개가 일치합니다.
신뢰 온도가 기본 36.5도보다 높습니다.
관심 분야가 1개 일치합니다(가산점 없음).
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,4,LOCALTIMESTAMP),
            (v_request_id,4,NULL,37.5,'요청 기술 2개 중 1개가 일치합니다.
협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,5,LOCALTIMESTAMP),
            (v_request_id,5,NULL,0.0,'협업 가능한 상태이며 요구 경력 필터를 통과했습니다.
포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.',NULL,6,LOCALTIMESTAMP),
            (v_request_id,NULL,7,80.51,'관심 분야가 1개 일치합니다.
기술 스택이 2개 일치합니다.
입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: RECRUITING',NULL,1,LOCALTIMESTAMP),
            (v_request_id,NULL,2,75.53,'관심 분야가 1개 일치합니다.
기술 스택이 2개 일치합니다.
입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: RECRUITMENT_COMPLETED',NULL,2,LOCALTIMESTAMP),
            (v_request_id,NULL,4,70.53,'관심 분야가 1개 일치합니다.
기술 스택이 2개 일치합니다.
입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: COMPLETED',NULL,3,LOCALTIMESTAMP),
            (v_request_id,NULL,8,28.06,'기술 스택이 1개 일치합니다.
입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: RECRUITING',NULL,4,LOCALTIMESTAMP),
            (v_request_id,NULL,5,28.0,'기술 스택이 1개 일치합니다.
입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: RECRUITING',NULL,5,LOCALTIMESTAMP),
            (v_request_id,NULL,3,18.03,'기술 스택이 1개 일치합니다.
입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: COMPLETED',NULL,6,LOCALTIMESTAMP),
            (v_request_id,NULL,6,10.5,'입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: RECRUITING',NULL,7,LOCALTIMESTAMP),
            (v_request_id,NULL,1,3.54,'입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.
모집·진행 상태: IN_PROGRESS',NULL,8,LOCALTIMESTAMP);
    END IF;
END;
$demo$;
COMMIT;

-- 데모 요청/결과 개수 확인
SELECT r.id,r.ai_conversation_id,r.ai_message_id,r.status,count(d.id) AS result_count
FROM ai_recommendation_requests r JOIN ai_conversations c ON c.id=r.ai_conversation_id JOIN users u ON u.id=c.user_id
LEFT JOIN ai_recommendations d ON d.recommended_request_id=r.id
WHERE u.email='member@devmozip.demo' AND r.idempotency_key IN ('devmozip-demo-ai-v1-button','devmozip-demo-ai-v1-message')
GROUP BY r.id ORDER BY r.created_at,r.id;
