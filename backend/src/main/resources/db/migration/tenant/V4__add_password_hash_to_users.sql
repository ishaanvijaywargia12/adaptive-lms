-- V4: Add password_hash column to tenant users table
-- Required for Spring Authorization Server form-based login.
-- Previously passwords were managed by Keycloak; now stored locally (BCrypt).

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
