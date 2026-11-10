-- 기존 음수 프로젝트 데이터 ID를 자동 생성 양수 ID로 이동. 외부 사용자 ID는 유지.
BEGIN;
SET LOCAL lock_timeout='5s';
LOCK TABLE public.projects, public.project_members, public.project_applications, public.project_application_portfolios, public.member_removal_votes, public.member_removal_ballots, public.reviews IN EXCLUSIVE MODE;
CREATE TEMP TABLE map_projects ON COMMIT DROP AS SELECT id AS old_id,nextval(pg_get_serial_sequence('public.projects','id')) AS new_id FROM public.projects WHERE id BETWEEN -920008 AND -920001 ORDER BY id DESC;
INSERT INTO public.projects SELECT (jsonb_populate_record(NULL::public.projects,to_jsonb(t)||jsonb_build_object('id',m.new_id))).* FROM public.projects t JOIN map_projects m ON t.id=m.old_id ;
CREATE TEMP TABLE map_project_members ON COMMIT DROP AS SELECT id AS old_id,nextval(pg_get_serial_sequence('public.project_members','id')) AS new_id FROM public.project_members WHERE id BETWEEN -930021 AND -930001 ORDER BY id DESC;
INSERT INTO public.project_members SELECT (jsonb_populate_record(NULL::public.project_members,to_jsonb(t)||jsonb_build_object('id',m.new_id,'project_id',m_project_id.new_id))).* FROM public.project_members t JOIN map_project_members m ON t.id=m.old_id JOIN map_projects m_project_id ON m_project_id.old_id=t.project_id;
CREATE TEMP TABLE map_project_applications ON COMMIT DROP AS SELECT id AS old_id,nextval(pg_get_serial_sequence('public.project_applications','id')) AS new_id FROM public.project_applications WHERE id BETWEEN -940006 AND -940001 ORDER BY id DESC;
INSERT INTO public.project_applications SELECT (jsonb_populate_record(NULL::public.project_applications,to_jsonb(t)||jsonb_build_object('id',m.new_id,'project_id',m_project_id.new_id))).* FROM public.project_applications t JOIN map_project_applications m ON t.id=m.old_id JOIN map_projects m_project_id ON m_project_id.old_id=t.project_id;
CREATE TEMP TABLE map_reviews ON COMMIT DROP AS SELECT id AS old_id,nextval(pg_get_serial_sequence('public.reviews','id')) AS new_id FROM public.reviews WHERE id BETWEEN -950009 AND -950001 ORDER BY id DESC;
INSERT INTO public.reviews SELECT (jsonb_populate_record(NULL::public.reviews,to_jsonb(t)||jsonb_build_object('id',m.new_id,'project_id',m_project_id.new_id,'reviewer_member_id',m_reviewer_member_id.new_id,'reviewee_member_id',m_reviewee_member_id.new_id))).* FROM public.reviews t JOIN map_reviews m ON t.id=m.old_id JOIN map_projects m_project_id ON m_project_id.old_id=t.project_id JOIN map_project_members m_reviewer_member_id ON m_reviewer_member_id.old_id=t.reviewer_member_id JOIN map_project_members m_reviewee_member_id ON m_reviewee_member_id.old_id=t.reviewee_member_id;
DELETE FROM public.reviews t USING map_reviews m WHERE t.id=m.old_id;
DELETE FROM public.project_applications t USING map_project_applications m WHERE t.id=m.old_id;
DELETE FROM public.project_members t USING map_project_members m WHERE t.id=m.old_id;
DELETE FROM public.projects t USING map_projects m WHERE t.id=m.old_id;
SELECT 'projects' AS table_name,old_id,new_id FROM map_projects UNION ALL SELECT 'project_members' AS table_name,old_id,new_id FROM map_project_members UNION ALL SELECT 'project_applications' AS table_name,old_id,new_id FROM map_project_applications UNION ALL SELECT 'reviews' AS table_name,old_id,new_id FROM map_reviews;
COMMIT;
