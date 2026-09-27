-- Additive subtype data only. No existing content, classification, IA or publication is changed.
CREATE TABLE post_restaurant_details (
 post_id BIGINT PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
 address VARCHAR(500) NOT NULL DEFAULT ''
);
CREATE TABLE post_publication_restaurant_details (
 post_id BIGINT PRIMARY KEY REFERENCES post_publications(post_id) ON DELETE CASCADE,
 address VARCHAR(500) NOT NULL
);
