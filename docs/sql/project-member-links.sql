-- 일반 회원을 1번으로 이동하고 누락된 Mock 개발자 및 프로젝트 회원 참조 연결.
-- 기존 회원의 비밀번호/역할/이메일은 변경하지 않는다. 새 개발자는 기존 데모 해시를 사용.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
LOCK TABLE public.users,public.ai_conversations,public.projects,public.project_members,public.project_applications IN EXCLUSIVE MODE;
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users WHERE id=0 AND email='member@devmozip.demo' AND nickname='김하늘')
 OR NOT EXISTS(SELECT 1 FROM users WHERE id=1 AND email='premium@devmozip.demo' AND nickname='이서준')
 THEN RAISE EXCEPTION '기존 회원 ID가 예상과 다릅니다. 변경을 중단합니다.'; END IF;
 IF EXISTS(SELECT 1 FROM users WHERE tag IN ('yuna','minjun','seoyeon','jiho') OR nickname IN ('이유나','박민준','최서연','정지호') OR email IN ('yuna@devmozip.demo','minjun@devmozip.demo','seoyeon@devmozip.demo','jiho@devmozip.demo'))
 THEN RAISE EXCEPTION '추가할 Mock 회원이 이미 존재합니다. 변경을 중단합니다.'; END IF;
END $$;
SELECT setval('public.users_id_seq',GREATEST((SELECT last_value FROM public.users_id_seq),(SELECT max(id) FROM users)),true);
CREATE TEMP TABLE user_id_move(old_id BIGINT PRIMARY KEY,new_id BIGINT UNIQUE) ON COMMIT DROP;
INSERT INTO user_id_move VALUES (1,nextval('public.users_id_seq')),(0,1);
UPDATE users u SET id=m.new_id FROM user_id_move m WHERE u.id=m.old_id AND m.old_id=1;
UPDATE users SET id=1 WHERE id=0;
-- 실회원 ID를 직접 참조하던 데이터는 기존 회원을 따라 이동한다.
UPDATE ai_conversations a SET user_id=m.new_id FROM user_id_move m WHERE a.user_id=m.old_id;
UPDATE projects p SET host_member_id=m.new_id FROM user_id_move m WHERE p.host_member_id=m.old_id;
UPDATE project_members p SET user_id=m.new_id FROM user_id_move m WHERE p.user_id=m.old_id;
UPDATE project_applications p SET applicant_member_id=m.new_id FROM user_id_move m WHERE p.applicant_member_id=m.old_id;
INSERT INTO users(email,password_hash,nickname,tag,role,status,trust_score,collaboration_status,summary,career,contact_hours,job_categories,skills,interests,introduction,introduction_format)
SELECT d.email,u.password_hash,d.nickname,d.tag,'USER','ACTIVE',d.temperature,'AVAILABLE',d.summary,d.career,'평일 20:00–23:00 · 주말 오후',d.jobs::jsonb,d.skills::jsonb,d.interests::jsonb,
 '사용자에게 필요한 가치를 고민하며 서비스를 만들고 있습니다.'||chr(10)||'좋은 코드를 넘어, 좋은 동료와 함께 성장하는 경험을 중요하게 생각해요.','text'
FROM (VALUES
 ('yuna@devmozip.demo','이유나','yuna',38.2,'사용자가 좋아하는 인터페이스를 함께 만들어요.','3년 차','["프론트엔드 개발자"]','["React","TypeScript","Figma"]','["생산성","커뮤니티"]'),
 ('minjun@devmozip.demo','박민준','minjun',39.1,'견고한 구조와 유연한 협업을 중요하게 생각합니다.','4년 차','["백엔드 개발자"]','["Java","Spring Boot","Python"]','["핀테크","생산성"]'),
 ('seoyeon@devmozip.demo','최서연','seoyeon',37.6,'일상에 도움이 되는 AI 서비스를 만들고 싶어요.','2년 차','["AI 엔지니어"]','["Python","TypeScript"]','["AI","교육"]'),
 ('jiho@devmozip.demo','정지호','jiho',38.6,'작은 아이디어를 빠르게 제품으로 만드는 개발자.','3년 차','["풀스택 개발자"]','["Node.js","React","TypeScript"]','["커뮤니티","교육"]')
) d(email,nickname,tag,temperature,summary,career,jobs,skills,interests)
JOIN users u ON u.email='premium@devmozip.demo';
CREATE TEMP TABLE project_mock_users(old_id BIGINT PRIMARY KEY,new_id BIGINT UNIQUE) ON COMMIT DROP;
INSERT INTO project_mock_users
SELECT d.old_id,u.id FROM (VALUES
 (-910001::BIGINT,'member@devmozip.demo'),(-910002::BIGINT,'premium@devmozip.demo'),
 (-910003::BIGINT,'yuna@devmozip.demo'),(-910004::BIGINT,'minjun@devmozip.demo'),
 (-910005::BIGINT,'seoyeon@devmozip.demo'),(-910006::BIGINT,'jiho@devmozip.demo')
) d(old_id,email) JOIN users u ON u.email=d.email;
UPDATE projects p SET host_member_id=m.new_id FROM project_mock_users m WHERE p.host_member_id=m.old_id;
UPDATE project_members p SET user_id=m.new_id FROM project_mock_users m WHERE p.user_id=m.old_id;
UPDATE project_applications p SET applicant_member_id=m.new_id FROM project_mock_users m WHERE p.applicant_member_id=m.old_id;
DO $$
BEGIN
 IF (SELECT count(*) FROM project_mock_users)<>6 THEN RAISE EXCEPTION 'Mock 회원 매핑 누락'; END IF;
 IF EXISTS(SELECT 1 FROM users WHERE id<=0) THEN RAISE EXCEPTION '양수가 아닌 회원 ID 잔존'; END IF;
 IF EXISTS(SELECT 1 FROM projects p LEFT JOIN users u ON u.id=p.host_member_id WHERE u.id IS NULL)
 OR EXISTS(SELECT 1 FROM project_members p LEFT JOIN users u ON u.id=p.user_id WHERE u.id IS NULL)
 OR EXISTS(SELECT 1 FROM project_applications p LEFT JOIN users u ON u.id=p.applicant_member_id WHERE u.id IS NULL)
 THEN RAISE EXCEPTION '회원 참조 불일치'; END IF;
END $$;
SELECT id,nickname,tag,role,status FROM users ORDER BY id;
COMMIT;
