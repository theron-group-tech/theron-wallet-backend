-- V1__create_customer_table.sql
-- Theron Wallet Service — Customer entity for Asaas integration

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE customer (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255)  NOT NULL,
    email            VARCHAR(255)  NOT NULL,
    cpf_cnpj         VARCHAR(14)   NOT NULL UNIQUE,
    phone            VARCHAR(20),
    mobile_phone     VARCHAR(20),
    asaas_customer_id VARCHAR(50)  UNIQUE,
    notification_disabled BOOLEAN  NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_cpf_cnpj ON customer(cpf_cnpj);
CREATE INDEX idx_customer_email ON customer(email);
CREATE INDEX idx_customer_asaas_id ON customer(asaas_customer_id);
