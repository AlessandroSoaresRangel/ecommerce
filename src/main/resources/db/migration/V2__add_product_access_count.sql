ALTER TABLE products ADD COLUMN access_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_products_access_count ON products(access_count DESC);
