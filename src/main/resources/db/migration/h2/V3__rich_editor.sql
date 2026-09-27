ALTER TABLE posts ADD COLUMN rich_content CLOB;
ALTER TABLE post_publications ADD COLUMN rich_content CLOB;
ALTER TABLE media ALTER COLUMN mime VARCHAR(120);
