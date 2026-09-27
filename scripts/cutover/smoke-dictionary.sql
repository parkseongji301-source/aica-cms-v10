-- 3C-1 operational-candidate vocabulary, approved for copied DB validation only.
-- No schema changes, test content, menu positions, or category-name inference.
SET AUTOCOMMIT FALSE;
INSERT INTO cohorts(code,name,sort_order)
SELECT 'COHORT_06','6기',6 WHERE NOT EXISTS(SELECT 1 FROM cohorts WHERE code='COHORT_06');
INSERT INTO cohorts(code,name,sort_order)
SELECT 'COHORT_07','7기',7 WHERE NOT EXISTS(SELECT 1 FROM cohorts WHERE code='COHORT_07');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'REVIEW_LIFE','생활','후기 콘텐츠의 생활 경험',0 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='REVIEW_LIFE');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'REVIEW_CLASS','수업','후기 콘텐츠의 수업 경험',1 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='REVIEW_CLASS');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'REVIEW_PROJECT','프로젝트','후기 콘텐츠의 프로젝트 경험',2 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='REVIEW_PROJECT');
INSERT INTO content_type_topics(type_code,topic_id)
SELECT 'REVIEW',t.id FROM topics t WHERE t.code IN ('REVIEW_LIFE','REVIEW_CLASS','REVIEW_PROJECT')
AND NOT EXISTS(SELECT 1 FROM content_type_topics a WHERE a.type_code='REVIEW' AND a.topic_id=t.id);
COMMIT;
-- 3C-2 candidate vocabulary; copied DB only, not a schema migration or content fixture.
SET AUTOCOMMIT FALSE;
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_PREPARATION','준비사항','FAQ 준비사항',10 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_PREPARATION');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_APPLICATION','지원·선발','FAQ 지원 및 선발',11 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_APPLICATION');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_CLASS','수업','FAQ 수업',12 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_CLASS');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_LIFE','생활','FAQ 생활 안내',13 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_LIFE');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_EMPLOYMENT','취업','FAQ 취업',14 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_EMPLOYMENT');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_ALLOWANCE','지원금','FAQ 지원금',15 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_ALLOWANCE');
INSERT INTO topics(code,name,description,sort_order)
SELECT 'FAQ_PROJECT','프로젝트','FAQ 프로젝트',16 WHERE NOT EXISTS(SELECT 1 FROM topics WHERE code='FAQ_PROJECT');
INSERT INTO content_type_topics(type_code,topic_id)
SELECT 'FAQ',t.id FROM topics t WHERE t.code IN ('FAQ_PREPARATION','FAQ_APPLICATION','FAQ_CLASS','FAQ_LIFE','FAQ_EMPLOYMENT','FAQ_ALLOWANCE','FAQ_PROJECT')
AND NOT EXISTS(SELECT 1 FROM content_type_topics a WHERE a.type_code='FAQ' AND a.topic_id=t.id);
COMMIT;
