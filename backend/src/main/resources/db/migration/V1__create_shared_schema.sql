-- V1: Shared public schema tables
-- Tenants registry and global configuration

CREATE TABLE IF NOT EXISTS public.tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    subdomain VARCHAR(100) NOT NULL UNIQUE,
    realm_name VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100) NOT NULL UNIQUE,
    plan VARCHAR(50) DEFAULT 'FREE',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS public.global_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(255) NOT NULL UNIQUE,
    value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

-- Initial global config
INSERT INTO public.global_configs (key, value) VALUES
    ('platform.name', 'Adaptive LMS'),
    ('platform.version', '1.0.0'),
    ('plagiarism.threshold', '70')
ON CONFLICT (key) DO NOTHING;
