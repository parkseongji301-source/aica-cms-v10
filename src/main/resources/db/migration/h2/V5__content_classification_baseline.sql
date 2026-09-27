-- Developer-registered types only. No type-specific fields or operator type creation.
INSERT INTO content_types(code,name,sort_order) VALUES
 ('GENERAL','일반',0),('REVIEW','후기',1),('RESTAURANT','맛집',2),('INTERVIEW','인터뷰',3),('FAQ','FAQ',4);
-- Do not infer meaning from legacy category names or copy draft data into publications.
UPDATE posts SET content_type_code='GENERAL' WHERE content_type_code IS NULL;
UPDATE post_publications SET content_type_code='GENERAL',type_name_snapshot='일반' WHERE content_type_code IS NULL;
-- Cohorts, topics and their links intentionally remain empty.
