-- Esquema de Base de Datos Cloudflare D1 para Servicios Compartidos Zero-Secret (AppOPT)

CREATE TABLE IF NOT EXISTS shared_services (
    service_id TEXT PRIMARY KEY,
    owner_email TEXT NOT NULL,
    issuer TEXT NOT NULL,
    account_name TEXT NOT NULL,
    encrypted_secret_b64 TEXT NOT NULL,
    iv_b64 TEXT NOT NULL,
    algorithm TEXT NOT NULL DEFAULT 'SHA1',
    digits INTEGER NOT NULL DEFAULT 6,
    period INTEGER NOT NULL DEFAULT 30,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shared_services_owner ON shared_services(owner_email);

CREATE TABLE IF NOT EXISTS share_grants (
    grant_id TEXT PRIMARY KEY,
    service_id TEXT NOT NULL,
    owner_email TEXT NOT NULL,
    guest_email TEXT NOT NULL,
    access_mode TEXT NOT NULL DEFAULT 'ONLINE_ONLY',
    batch_hours INTEGER NOT NULL DEFAULT 24,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (service_id) REFERENCES shared_services(service_id) ON DELETE CASCADE,
    UNIQUE(service_id, guest_email)
);

CREATE INDEX IF NOT EXISTS idx_share_grants_owner ON share_grants(owner_email);
CREATE INDEX IF NOT EXISTS idx_share_grants_guest ON share_grants(guest_email);
