-- Manual verification vocabulary ONLY. Never add this file to Flyway or a startup initializer.
-- Apply with scripts/seed-classification-copy.ps1 to a stopped .cache copy. No posts or IA are inserted.
SET AUTOCOMMIT FALSE;
MERGE INTO cohorts(code,name,sort_order) KEY(code) VALUES('B2B_C6','6기',0),('B2B_C7','7기',1);
MERGE INTO topics(code,name,sort_order) KEY(code) VALUES
 ('B2B_REVIEW_LIFE','생활',0),('B2B_REVIEW_CLASS','수업',1),('B2B_REVIEW_PROJECT','프로젝트',2),
 ('B2B_FAQ_PREPARATION','준비사항',3),('B2B_FAQ_LIFE','생활',4);
INSERT INTO content_type_topics(type_code,topic_id)
 SELECT 'REVIEW',t.id FROM topics t WHERE t.code IN ('B2B_REVIEW_LIFE','B2B_REVIEW_CLASS','B2B_REVIEW_PROJECT')
 AND NOT EXISTS(SELECT 1 FROM content_type_topics a WHERE a.type_code='REVIEW' AND a.topic_id=t.id);
INSERT INTO content_type_topics(type_code,topic_id)
 SELECT 'FAQ',t.id FROM topics t WHERE t.code IN ('B2B_FAQ_PREPARATION','B2B_FAQ_LIFE')
 AND NOT EXISTS(SELECT 1 FROM content_type_topics a WHERE a.type_code='FAQ' AND a.topic_id=t.id);
COMMIT;
