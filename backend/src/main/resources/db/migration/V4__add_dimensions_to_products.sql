ALTER TABLE products
    ADD COLUMN weight_kg NUMERIC(6,3) NOT NULL DEFAULT 0.300 CHECK (weight_kg > 0),
    ADD COLUMN height_cm INTEGER      NOT NULL DEFAULT 10    CHECK (height_cm > 0),
    ADD COLUMN width_cm  INTEGER      NOT NULL DEFAULT 10    CHECK (width_cm > 0),
    ADD COLUMN length_cm INTEGER      NOT NULL DEFAULT 10    CHECK (length_cm > 0);