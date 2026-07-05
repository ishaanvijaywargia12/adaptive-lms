-- PostgreSQL initialization script
-- Creates keycloak schema for Keycloak storage

CREATE SCHEMA IF NOT EXISTS keycloak;

-- The public schema is used for shared LMS tables (tenants, global_configs)
-- Per-tenant schemas are created dynamically by the application
