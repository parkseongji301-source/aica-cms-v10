-- Structure membership, separate from menu visibility. An area removed from the site structure
-- (in_structure = FALSE) leaves the draft composition and, after the next structure publication, the
-- public structure; the page itself, its publication and its content stay. Every existing page stays in
-- the structure; no existing row is rewritten. Removal rules (no children left in the structure, no
-- content-work link) are service rules, not constraints here.
ALTER TABLE site_pages ADD COLUMN in_structure BOOLEAN NOT NULL DEFAULT TRUE;
