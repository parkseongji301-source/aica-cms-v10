ALTER TABLE posts ALTER COLUMN content_type_code SET NOT NULL;
ALTER TABLE posts ALTER COLUMN content_type_code SET DEFAULT 'GENERAL';
ALTER TABLE post_publications ALTER COLUMN content_type_code SET NOT NULL;
ALTER TABLE post_publications ALTER COLUMN type_name_snapshot SET NOT NULL;
-- Publications deliberately have no default: an old publisher must not silently lose classifications.
