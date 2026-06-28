CREATE DATABASE IF NOT EXISTS `kafka_app`;
USE `kafka_app`;

CREATE TABLE IF NOT EXISTS `orders` (
    `order_id` VARCHAR(64) NOT NULL PRIMARY KEY,
    `customer_id` VARCHAR(64) NOT NULL,
    `amount` DOUBLE NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `created_at` BIGINT NOT NULL
);
