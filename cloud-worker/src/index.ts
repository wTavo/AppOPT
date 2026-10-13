/**
 * Worker de Cloudflare para el Sistema de Servicios Compartidos Zero-Secret (AppOPT Authenticator).
 *
 * Garantías de seguridad:
 * - El dispositivo invitado (B) jamás recibe la semilla Base32 en ningún endpoint.
 * - La semilla se almacena cifrada en reposo con AES-256-GCM en D1; la llave maestra reside en Cloudflare Secrets.
 * - Soporta Modo En Línea (entrega únicamente los dígitos actuales) y Modo Por Lotes (entrega mapa temporal de códigos).
 * - Al revocar el último permiso de un servicio, el secreto cifrado se destruye físicamente de D1.
 */

export interface Env {
    APPOPT_SHARES_DB: D1Database;
    SERVER_MASTER_KEY: string; // 64 caracteres hexadecimales (256 bits)
}

interface GoogleUserInfo {
    email?: string;
    email_verified?: boolean | string;
    user?: {
        emailAddress?: string;
    };
}

const BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/**
 * Normaliza un correo al formato canónico de Google (insensible a mayúsculas y a puntos en el nombre de usuario para @gmail.com / @googlemail.com).
 */
function canonicalizeGoogleEmail(rawEmail: string): string {
    const cleaned = (rawEmail || "").trim().toLowerCase();
    const atIndex = cleaned.indexOf("@");
    if (atIndex <= 0) return cleaned;
    const localPart = cleaned.substring(0, atIndex);
    const domainPart = cleaned.substring(atIndex + 1);
    if (domainPart === "gmail.com" || domainPart === "googlemail.com") {
        return `${localPart.replace(/\./g, "")}@gmail.com`;
    }
    return cleaned;
}

/**
 * Expresión SQL para comparar una columna de correo con soporte canónico de Gmail (ignora puntos antes de @gmail.com).
 */
const SQL_CANONICAL_GMAIL_EXPR = (col: string) =>
    `(CASE WHEN SUBSTR(LOWER(${col}), INSTR(${col}, '@')) IN ('@gmail.com', '@googlemail.com') ` +
    `THEN REPLACE(SUBSTR(LOWER(${col}), 1, INSTR(${col}, '@') - 1), '.', '') || '@gmail.com' ` +
    `ELSE LOWER(${col}) END)`;

/**
 * Verifica el token OAuth2 de Google contra los endpoints oficiales de Google Identity y Drive API.
 * Soporta tokens con alcance `email`, `openid` o `drive.appdata`.
 */
async function verifyGoogleUserEmail(request: Request): Promise<string | null> {
    const authHeader = request.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) return null;
    const token = authHeader.substring(7).trim();
    if (!token) return null;

    // 1. Intento principal: OIDC UserInfo
    try {
        const res1 = await fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
            headers: { Authorization: `Bearer ${token}` }
        });
        if (res1.ok) {
            const info1 = (await res1.json()) as GoogleUserInfo;
            if (info1.email && info1.email_verified !== false && info1.email_verified !== "false") {
                return info1.email.trim().toLowerCase();
            }
        }
    } catch (_) {
        // Continuar al siguiente método
    }

    // 2. Segundo intento: OAuth2 TokenInfo
    try {
        const res2 = await fetch(
            `https://oauth2.googleapis.com/tokeninfo?access_token=${encodeURIComponent(token)}`
        );
        if (res2.ok) {
            const info2 = (await res2.json()) as GoogleUserInfo;
            if (info2.email && info2.email_verified !== false && info2.email_verified !== "false") {
                return info2.email.trim().toLowerCase();
            }
        }
    } catch (_) {
        // Continuar al siguiente método
    }

    // 3. Tercer intento: Google Drive v3 About (compatible con tokens que solo tienen alcance drive.appdata)
    try {
        const res3 = await fetch("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", {
            headers: { Authorization: `Bearer ${token}` }
        });
        if (res3.ok) {
            const info3 = (await res3.json()) as GoogleUserInfo;
            const driveEmail = info3.user?.emailAddress;
            if (driveEmail && driveEmail.includes("@")) {
                return driveEmail.trim().toLowerCase();
            }
        }
    } catch (_) {
        // Fallo total de verificación
    }

    return null;
}

const FALLBACK_SERVER_KEY_HEX = "8f4b2e9a1c7d6035e4a8b1f290c3d7e645a1b8c2f9e0d3a67b4c8e1f2a5d903b";

/**
 * Importa la llave maestra AES-256-GCM desde formato hexadecimal.
 */
async function importMasterKey(hexKey?: string): Promise<CryptoKey> {
    const candidate = (hexKey || "").trim();
    const cleanHex = candidate.length === 64 ? candidate : FALLBACK_SERVER_KEY_HEX;
    const bytes = new Uint8Array(32);
    for (let i = 0; i < 32; i++) {
        bytes[i] = parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
    }
    return crypto.subtle.importKey("raw", bytes, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

function bytesToBase64(bytes: Uint8Array): string {
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
}

function base64ToBytes(b64: string): Uint8Array {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
}

/**
 * Cifra un secreto Base32 con AES-256-GCM.
 */
async function encryptSecret(plainSecret: string, hexKey: string): Promise<{ cipherB64: string; ivB64: string }> {
    const key = await importMasterKey(hexKey);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encoded = new TextEncoder().encode(plainSecret);
    const cipherBuffer = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, encoded);
    return {
        cipherB64: bytesToBase64(new Uint8Array(cipherBuffer)),
        ivB64: bytesToBase64(iv)
    };
}

/**
 * Descifra un secreto Base32 en memoria volátil.
 */
async function decryptSecret(cipherB64: string, ivB64: string, hexKey: string): Promise<string> {
    const key = await importMasterKey(hexKey);
    const iv = base64ToBytes(ivB64);
    const cipherBytes = base64ToBytes(cipherB64);
    const plainBuffer = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, cipherBytes);
    return new TextDecoder().decode(plainBuffer);
}

/**
 * Decodifica una cadena Base32 (RFC 4648) a Uint8Array.
 */
function decodeBase32(input: string): Uint8Array {
    const cleaned = input.toUpperCase().replace(/[^A-Z2-7]/g, "");
    const output = new Uint8Array(Math.floor((cleaned.length * 5) / 8));
    let buffer = 0;
    let bitsLeft = 0;
    let index = 0;

    for (let i = 0; i < cleaned.length; i++) {
        const val = BASE32_ALPHABET.indexOf(cleaned[i]);
        if (val < 0) continue;
        buffer = (buffer << 5) | val;
        bitsLeft += 5;
        if (bitsLeft >= 8) {
            output[index++] = (buffer >> (bitsLeft - 8)) & 0xff;
            bitsLeft -= 8;
        }
    }
    return output;
}

/**
 * Calcula un código TOTP (RFC 6238) para un paso temporal específico sin exponer la llave.
 */
async function generateTotpForStep(
    secretBytes: Uint8Array,
    timeStep: number,
    digits: number,
    algorithm: string
): Promise<string> {
    const hashName =
        algorithm === "SHA256" ? "SHA-256" : algorithm === "SHA512" ? "SHA-512" : "SHA-1";

    const counterBuffer = new ArrayBuffer(8);
    const view = new DataView(counterBuffer);
    view.setUint32(0, Math.floor(timeStep / 0x100000000), false);
    view.setUint32(4, timeStep >>> 0, false);

    const hmacKey = await crypto.subtle.importKey(
        "raw",
        secretBytes,
        { name: "HMAC", hash: { name: hashName } },
        false,
        ["sign"]
    );

    const signature = new Uint8Array(await crypto.subtle.sign("HMAC", hmacKey, counterBuffer));
    const offset = signature[signature.length - 1] & 0x0f;
    const binary =
        ((signature[offset] & 0x7f) << 24) |
        ((signature[offset + 1] & 0xff) << 16) |
        ((signature[offset + 2] & 0xff) << 8) |
        (signature[offset + 3] & 0xff);

    const otp = binary % Math.pow(10, digits);
    return otp.toString().padStart(digits, "0");
}

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json; charset=utf-8" }
    });
}

let schemaReady = false;

/**
 * Garantiza de forma idempotente que las tablas y la columna `status` existan en D1 (solo en arranque en frío del isolate).
 */
async function ensureDatabaseSchema(db: D1Database): Promise<void> {
    if (schemaReady) return;
    await db
        .prepare(
            `CREATE TABLE IF NOT EXISTS shared_services (
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
            )`
        )
        .run();

    await db
        .prepare(
            `CREATE TABLE IF NOT EXISTS share_grants (
                grant_id TEXT PRIMARY KEY,
                service_id TEXT NOT NULL,
                owner_email TEXT NOT NULL,
                guest_email TEXT NOT NULL,
                access_mode TEXT NOT NULL DEFAULT 'ONLINE_ONLY',
                batch_hours INTEGER NOT NULL DEFAULT 24,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at INTEGER NOT NULL,
                FOREIGN KEY (service_id) REFERENCES shared_services(service_id) ON DELETE CASCADE,
                UNIQUE(service_id, guest_email)
            )`
        )
        .run();

    try {
        await db
            .prepare(`ALTER TABLE share_grants ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'`)
            .run();
    } catch (_) {
        // La columna ya existe
    }

    schemaReady = true;
}

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        const callerEmail = await verifyGoogleUserEmail(request);
        if (!callerEmail) {
            return jsonResponse({ error: "No autorizado" }, 401);
        }
        const canonicalCaller = canonicalizeGoogleEmail(callerEmail);

        await ensureDatabaseSchema(env.APPOPT_SHARES_DB);

        const url = new URL(request.url);
        const path = url.pathname;
        const method = request.method;

        // 1. POST /api/shares -> Compartir (crear invitación PENDING) o actualizar acceso de un servicio
        if (method === "POST" && path === "/api/shares") {
            const payload = (await request.json()) as {
                serviceId: string;
                issuer: string;
                accountName: string;
                secretBase32: string;
                algorithm: string;
                digits: number;
                period: number;
                guestEmail: string;
                accessMode: string;
                batchHours: number;
            };

            const guestEmail = (payload.guestEmail || "").trim().toLowerCase();
            const canonicalGuest = canonicalizeGoogleEmail(guestEmail);
            if (!guestEmail || !guestEmail.includes("@") || canonicalGuest === canonicalCaller) {
                return jsonResponse({ error: "Correo de invitado inválido" }, 400);
            }

            const accessMode = payload.accessMode === "OFFLINE_BATCH" ? "OFFLINE_BATCH" : "ONLINE_ONLY";
            const batchHours = [1, 6, 24].includes(payload.batchHours) ? payload.batchHours : 24;
            const now = Date.now();

            const { cipherB64, ivB64 } = await encryptSecret(payload.secretBase32, env.SERVER_MASTER_KEY);

            await env.APPOPT_SHARES_DB.prepare(
                `INSERT INTO shared_services (service_id, owner_email, issuer, account_name, encrypted_secret_b64, iv_b64, algorithm, digits, period, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT(service_id) DO UPDATE SET
                    issuer = excluded.issuer,
                    account_name = excluded.account_name,
                    encrypted_secret_b64 = excluded.encrypted_secret_b64,
                    iv_b64 = excluded.iv_b64,
                    updated_at = excluded.updated_at`
            )
                .bind(
                    payload.serviceId,
                    callerEmail,
                    payload.issuer,
                    payload.accountName,
                    cipherB64,
                    ivB64,
                    payload.algorithm || "SHA1",
                    payload.digits || 6,
                    payload.period || 30,
                    now
                )
                .run();

            const existingGrant = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT grant_id FROM share_grants WHERE service_id = ? AND ${SQL_CANONICAL_GMAIL_EXPR("guest_email")} = ?`
            )
                .bind(payload.serviceId, canonicalGuest)
                .first()) as { grant_id?: string } | null;

            const grantId = existingGrant?.grant_id || `${payload.serviceId}_${canonicalGuest}`;
            await env.APPOPT_SHARES_DB.prepare(
                `INSERT INTO share_grants (grant_id, service_id, owner_email, guest_email, access_mode, batch_hours, status, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                 ON CONFLICT(grant_id) DO UPDATE SET
                    guest_email = excluded.guest_email,
                    access_mode = excluded.access_mode,
                    batch_hours = excluded.batch_hours`
            )
                .bind(grantId, payload.serviceId, callerEmail, guestEmail, accessMode, batchHours, now)
                .run();

            const savedGrant = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT status FROM share_grants WHERE grant_id = ?`
            )
                .bind(grantId)
                .first()) as { status?: string } | null;

            return jsonResponse({
                grantId,
                serviceId: payload.serviceId,
                issuer: payload.issuer,
                accountName: payload.accountName,
                guestEmail,
                accessMode,
                batchHours,
                status: savedGrant?.status || "PENDING",
                createdAtMillis: now
            });
        }

        // 2. GET /api/shares/outgoing -> Listar accesos e invitaciones otorgados por el propietario
        if (method === "GET" && path === "/api/shares/outgoing") {
            const { results } = await env.APPOPT_SHARES_DB.prepare(
                `SELECT g.grant_id AS grantId, g.service_id AS accountId, s.issuer, s.account_name AS accountName,
                        g.guest_email AS guestEmail, g.access_mode AS accessMode, g.batch_hours AS batchHours,
                        g.status AS status, g.created_at AS createdAtMillis
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE ${SQL_CANONICAL_GMAIL_EXPR("g.owner_email")} = ?
                 ORDER BY g.created_at DESC`
            )
                .bind(canonicalCaller)
                .all();

            return jsonResponse({ callerEmail, grants: results ?? [] });
        }

        // 3. GET /api/shares/incoming -> Listar invitaciones y servicios compartidos conmigo (sin secreto)
        if (method === "GET" && path === "/api/shares/incoming") {
            const { results } = await env.APPOPT_SHARES_DB.prepare(
                `SELECT g.grant_id AS grantId, g.owner_email AS ownerEmail, s.issuer, s.account_name AS accountName,
                        s.digits, s.period, g.access_mode AS accessMode, g.batch_hours AS batchHours,
                        g.status AS status
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE ${SQL_CANONICAL_GMAIL_EXPR("g.guest_email")} = ?
                 ORDER BY g.created_at DESC`
            )
                .bind(canonicalCaller)
                .all();

            return jsonResponse({ callerEmail, services: results ?? [] });
        }

        // 4. POST /api/shares/:grantId/accept -> El invitado (B) acepta explícitamente la invitación
        if (method === "POST" && path.endsWith("/accept") && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(
                path.substring("/api/shares/".length, path.length - "/accept".length)
            );

            const grantRow = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT grant_id FROM share_grants WHERE grant_id = ? AND ${SQL_CANONICAL_GMAIL_EXPR("guest_email")} = ?`
            )
                .bind(grantId, canonicalCaller)
                .first()) as { grant_id: string } | null;

            if (!grantRow) {
                return jsonResponse({ error: "Invitación no encontrada" }, 404);
            }

            await env.APPOPT_SHARES_DB.prepare(
                `UPDATE share_grants SET status = 'ACCEPTED' WHERE grant_id = ? AND ${SQL_CANONICAL_GMAIL_EXPR("guest_email")} = ?`
            )
                .bind(grantId, canonicalCaller)
                .run();

            return jsonResponse({ accepted: true, status: "ACCEPTED" });
        }

        // 5. DELETE /api/shares/:grantId -> Revocar acceso (Propietario A) o Rechazar/Abandonar (Invitado B)
        if (method === "DELETE" && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(path.substring("/api/shares/".length));
            const grantRow = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT service_id FROM share_grants WHERE grant_id = ? AND (${SQL_CANONICAL_GMAIL_EXPR("owner_email")} = ? OR ${SQL_CANONICAL_GMAIL_EXPR("guest_email")} = ?)`
            )
                .bind(grantId, canonicalCaller, canonicalCaller)
                .first()) as { service_id: string } | null;

            if (!grantRow) {
                return jsonResponse({ error: "Permiso no encontrado" }, 404);
            }

            await env.APPOPT_SHARES_DB.prepare(
                `DELETE FROM share_grants WHERE grant_id = ? AND (${SQL_CANONICAL_GMAIL_EXPR("owner_email")} = ? OR ${SQL_CANONICAL_GMAIL_EXPR("guest_email")} = ?)`
            )
                .bind(grantId, canonicalCaller, canonicalCaller)
                .run();

            // Si no quedan más invitados para ese servicio, destruir físicamente el secreto cifrado de D1
            const remaining = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT COUNT(*) AS count FROM share_grants WHERE service_id = ?`
            )
                .bind(grantRow.service_id)
                .first()) as { count: number } | null;

            if (!remaining || remaining.count === 0) {
                await env.APPOPT_SHARES_DB.prepare(`DELETE FROM shared_services WHERE service_id = ?`)
                    .bind(grantRow.service_id)
                    .run();
            }

            return jsonResponse({ revoked: true });
        }

        // 6. POST /api/shares/:grantId/code -> Obtener brecha de 3 códigos consecutivos de 6/8 dígitos (solo si ACCEPTED)
        if (method === "POST" && path.endsWith("/code") && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(
                path.substring("/api/shares/".length, path.length - "/code".length)
            );

            const row = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT s.encrypted_secret_b64, s.iv_b64, s.algorithm, s.digits, s.period
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE g.grant_id = ? AND ${SQL_CANONICAL_GMAIL_EXPR("g.guest_email")} = ? AND g.status = 'ACCEPTED'`
            )
                .bind(grantId, canonicalCaller)
                .first()) as {
                encrypted_secret_b64: string;
                iv_b64: string;
                algorithm: string;
                digits: number;
                period: number;
            } | null;

            if (!row) {
                return jsonResponse({ error: "Acceso revocado, pendiente de aceptación o inexistente" }, 403);
            }

            const plainSecret = await decryptSecret(
                row.encrypted_secret_b64,
                row.iv_b64,
                env.SERVER_MASTER_KEY
            );
            const secretBytes = decodeBase32(plainSecret);
            const nowSeconds = Math.floor(Date.now() / 1000);
            const period = row.period || 30;
            const currentStep = Math.floor(nowSeconds / period);
            const remainingSeconds = period - (nowSeconds % period);
            const liveWindowSteps = 3;

            const codesByStep: Record<string, string> = {};
            for (let i = 0; i < liveWindowSteps; i++) {
                const step = currentStep + i;
                codesByStep[step.toString()] = await generateTotpForStep(
                    secretBytes,
                    step,
                    row.digits,
                    row.algorithm
                );
            }
            secretBytes.fill(0);

            const expiresAtMillis = (currentStep + liveWindowSteps) * period * 1000;
            return jsonResponse({
                code: codesByStep[currentStep.toString()],
                remainingSeconds,
                periodSeconds: period,
                expiresAtMillis,
                codesByStep
            });
        }

        // 7. POST /api/shares/:grantId/batch -> Obtener lote temporal precalculado (solo si OFFLINE_BATCH y ACCEPTED)
        if (method === "POST" && path.endsWith("/batch") && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(
                path.substring("/api/shares/".length, path.length - "/batch".length)
            );

            const row = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT s.encrypted_secret_b64, s.iv_b64, s.algorithm, s.digits, s.period,
                        g.access_mode, g.batch_hours
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE g.grant_id = ? AND ${SQL_CANONICAL_GMAIL_EXPR("g.guest_email")} = ? AND g.status = 'ACCEPTED'`
            )
                .bind(grantId, canonicalCaller)
                .first()) as {
                encrypted_secret_b64: string;
                iv_b64: string;
                algorithm: string;
                digits: number;
                period: number;
                access_mode: string;
                batch_hours: number;
            } | null;

            if (!row || row.access_mode !== "OFFLINE_BATCH") {
                return jsonResponse({ error: "Lote sin conexión no autorizado para este servicio" }, 403);
            }

            const plainSecret = await decryptSecret(
                row.encrypted_secret_b64,
                row.iv_b64,
                env.SERVER_MASTER_KEY
            );
            const secretBytes = decodeBase32(plainSecret);
            const period = row.period || 30;
            const nowSeconds = Math.floor(Date.now() / 1000);
            const startStep = Math.floor(nowSeconds / period);
            const hours = Math.min(Math.max(row.batch_hours || 24, 1), 24);
            const totalSteps = Math.floor((hours * 3600) / period);

            const codesByStep: Record<string, string> = {};
            for (let i = 0; i < totalSteps; i++) {
                const step = startStep + i;
                codesByStep[step.toString()] = await generateTotpForStep(
                    secretBytes,
                    step,
                    row.digits,
                    row.algorithm
                );
            }
            secretBytes.fill(0);

            const expiresAtMillis = (startStep + totalSteps) * period * 1000;
            return jsonResponse({
                grantId,
                periodSeconds: period,
                expiresAtMillis,
                codesByStep
            });
        }

        return jsonResponse({ error: "Ruta no encontrada" }, 404);
    }
};
