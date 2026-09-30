-- Page hierarchy. Existing pages become top-level (parent_id NULL, sort_order 0); no data is rewritten.
-- Only general integrity lives here; operating limits (two levels, fixed top-level home page) are service/UI rules.
ALTER TABLE site_pages ADD COLUMN parent_id BIGINT;
ALTER TABLE site_pages ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
-- No cascade: a page with child pages cannot be deleted.
ALTER TABLE site_pages ADD CONSTRAINT site_pages_parent_fk FOREIGN KEY(parent_id) REFERENCES site_pages(id);
ALTER TABLE site_pages ADD CONSTRAINT site_pages_not_own_parent CHECK(parent_id IS NULL OR parent_id <> id);
CREATE INDEX site_pages_parent_order ON site_pages(parent_id, sort_order, id);
