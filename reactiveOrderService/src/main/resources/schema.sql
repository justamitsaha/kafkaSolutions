--mysql
use `amit`;

CREATE TABLE IF NOT EXISTS orders (
    order_id CHAR(36) NOT NULL PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS order_outbox (
    id CHAR(36) NOT NULL PRIMARY KEY,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error TEXT NULL,
    attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT check_blocked_customer CHECK (payload NOT LIKE '%"customerId":"BLOCKED"%')
);

CREATE INDEX idx_order_outbox_status_available
    ON order_outbox (status, available_at);

use `amit`;
TRUNCATE `amit`.`order_outbox`;
TRUNCATE `amit`.`orders`;
TRUNCATE `amit`.`customers`;

SELECT * FROM amit.orders;

SELECT * FROM amit.order_outbox;

SELECT * FROM amit.customers;


-- postgres
-- Create the schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS microservice;

CREATE TABLE IF NOT EXISTS microservice.orders (
    order_id CHAR(36) NOT NULL PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL
    );

CREATE TABLE IF NOT EXISTS microservice.order_outbox (
    id CHAR(36) NOT NULL PRIMARY KEY,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error TEXT NULL,
    attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT check_blocked_customer CHECK (payload NOT LIKE '%"customerId":"BLOCKED"%')
    );

CREATE INDEX microservice.idx_order_outbox_status_available
    ON order_outbox (status, available_at);


TRUNCATE microservice.order_outbox;
TRUNCATE microservice.orders;
TRUNCATE microservice.customers;

SELECT * FROM microservice.orders;

SELECT * FROM microservice.order_outbox;

SELECT * FROM microservice.customers;
