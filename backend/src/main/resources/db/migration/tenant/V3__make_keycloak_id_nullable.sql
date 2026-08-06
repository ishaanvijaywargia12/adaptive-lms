-- V3: Make keycloak_id nullable to support self-hosted auth
-- Demo users are seeded without a keycloak_id (filled on first OAuth2 login)

ALTER TABLE users ALTER COLUMN keycloak_id DROP NOT NULL;
