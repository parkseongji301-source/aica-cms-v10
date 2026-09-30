-- 콘텐츠 작업에 보이기 (V17). A page appears in the admin 콘텐츠 작업 sidebar only when the operator turns this
-- on, independently of the content-type link: a page without a type is just an entry that opens the page
-- itself. Existing rows keep the default (FALSE); the operator turns it on per page. No other change.
ALTER TABLE site_pages ADD COLUMN content_work_visible BOOLEAN NOT NULL DEFAULT FALSE;
