CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150)  NOT NULL,
    email           VARCHAR(150)  NOT NULL UNIQUE,
    password        VARCHAR(255)  NOT NULL,
    role            VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL UNIQUE,
    description     VARCHAR(500)
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150)   NOT NULL,
    description     VARCHAR(1000),
    price           NUMERIC(10,2)  NOT NULL CHECK (price >= 0),
    stock_quantity  INTEGER        NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    image_url       VARCHAR(500),
    active          BOOLEAN        NOT NULL DEFAULT true,
    category_id     BIGINT         REFERENCES categories(id),
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(active);

CREATE TABLE carts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL UNIQUE REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id              BIGSERIAL PRIMARY KEY,
    cart_id         BIGINT      NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      BIGINT      NOT NULL REFERENCES products(id),
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    UNIQUE(cart_id, product_id)
);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users(id),
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(10,2)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user ON orders(user_id);

CREATE TABLE order_items (
    id                       BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id                BIGINT         NOT NULL REFERENCES products(id),
    quantity                 INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price_at_purchase   NUMERIC(10,2)  NOT NULL
);

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT      NOT NULL UNIQUE REFERENCES orders(id),
    method          VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_id  VARCHAR(100),
    paid_at         TIMESTAMP
);
