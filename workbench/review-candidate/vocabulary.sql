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
