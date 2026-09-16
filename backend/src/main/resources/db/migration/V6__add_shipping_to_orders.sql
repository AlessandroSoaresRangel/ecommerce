ALTER TABLE orders ADD COLUMN shipping_cost NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN shipping_carrier_name VARCHAR(100);
ALTER TABLE orders ADD COLUMN shipping_service_name VARCHAR(100);
