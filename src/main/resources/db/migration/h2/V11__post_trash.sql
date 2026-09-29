-- Only explicit trash operations are recoverable. Existing deleted_at tombstones
-- represent the old permanent-delete flow and must never become restorable.
CREATE TABLE post_trash (
 post_id BIGINT PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE
);
