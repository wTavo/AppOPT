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
    email_verified?: boolean;
}

const BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/**
 * Verifica el token OAuth2 de Google contra la API oficial de Google Identity.
 */
async function verifyGoogleUserEmail(request: Request): Promise<string | null> {
    const authHeader = request.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) return null;
    const token = authHeader.substring(7).trim();
    if (!token) return null;

    const response = await fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
        headers: { Authorization: `Bearer ${token}` }
    });
    if (!response.ok) return null;
    const info = (await response.json()) as GoogleUserInfo;
    if (!info.email || info.email_verified === false) return null;
    return info.email.trim().toLowerCase();
}

/**
 * Importa la llave maestra AES-256-GCM desde formato hexadecimal.
 */
async function importMasterKey(hexKey: string): Promise<CryptoKey> {
    const cleanHex = hexKey.trim();
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

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        const callerEmail = await verifyGoogleUserEmail(request);
        if (!callerEmail) {
            return jsonResponse({ error: "No autorizado" }, 401);
        }

        const url = new URL(request.url);
        const path = url.pathname;
        const method = request.method;

        // 1. POST /api/shares -> Compartir o actualizar acceso de un servicio
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
            if (!guestEmail || !guestEmail.includes("@") || guestEmail === callerEmail) {
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

            const grantId = `${payload.serviceId}_${guestEmail}`;
            await env.APPOPT_SHARES_DB.prepare(
                `INSERT INTO share_grants (grant_id, service_id, owner_email, guest_email, access_mode, batch_hours, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT(service_id, guest_email) DO UPDATE SET
                    access_mode = excluded.access_mode,
                    batch_hours = excluded.batch_hours`
            )
                .bind(grantId, payload.serviceId, callerEmail, guestEmail, accessMode, batchHours, now)
                .run();

            return jsonResponse({
                grantId,
                serviceId: payload.serviceId,
                issuer: payload.issuer,
                accountName: payload.accountName,
                guestEmail,
                accessMode,
                batchHours,
                createdAtMillis: now
            });
        }

        // 2. GET /api/shares/outgoing -> Listar accesos otorgados por el propietario
        if (method === "GET" && path === "/api/shares/outgoing") {
            const { results } = await env.APPOPT_SHARES_DB.prepare(
                `SELECT g.grant_id AS grantId, g.service_id AS accountId, s.issuer, s.account_name AS accountName,
                        g.guest_email AS guestEmail, g.access_mode AS accessMode, g.batch_hours AS batchHours,
                        g.created_at AS createdAtMillis
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE g.owner_email = ?
                 ORDER BY g.created_at DESC`
            )
                .bind(callerEmail)
                .all();

            return jsonResponse({ grants: results ?? [] });
        }

        // 3. GET /api/shares/incoming -> Listar servicios compartidos conmigo (sin secreto)
        if (method === "GET" && path === "/api/shares/incoming") {
            const { results } = await env.APPOPT_SHARES_DB.prepare(
                `SELECT g.grant_id AS grantId, g.owner_email AS ownerEmail, s.issuer, s.account_name AS accountName,
                        s.digits, s.period, g.access_mode AS accessMode, g.batch_hours AS batchHours
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE g.guest_email = ?
                 ORDER BY g.created_at DESC`
            )
                .bind(callerEmail)
                .all();

            return jsonResponse({ services: results ?? [] });
        }

        // 4. DELETE /api/shares/:grantId -> Revocar acceso instantáneamente
        if (method === "DELETE" && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(path.substring("/api/shares/".length));
            const grantRow = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT service_id FROM share_grants WHERE grant_id = ? AND owner_email = ?`
            )
                .bind(grantId, callerEmail)
                .first()) as { service_id: string } | null;

            if (!grantRow) {
                return jsonResponse({ error: "Permiso no encontrado" }, 404);
            }

            await env.APPOPT_SHARES_DB.prepare(
                `DELETE FROM share_grants WHERE grant_id = ? AND owner_email = ?`
            )
                .bind(grantId, callerEmail)
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

        // 5. POST /api/shares/:grantId/code -> Obtener únicamente el código actual de 6/8 dígitos
        if (method === "POST" && path.endsWith("/code") && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(
                path.substring("/api/shares/".length, path.length - "/code".length)
            );

            const row = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT s.encrypted_secret_b64, s.iv_b64, s.algorithm, s.digits, s.period
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE g.grant_id = ? AND g.guest_email = ?`
            )
                .bind(grantId, callerEmail)
                .first()) as {
                encrypted_secret_b64: string;
                iv_b64: string;
                algorithm: string;
                digits: number;
                period: number;
            } | null;

            if (!row) {
                return jsonResponse({ error: "Acceso revocado o inexistente" }, 403);
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

            const code = await generateTotpForStep(secretBytes, currentStep, row.digits, row.algorithm);
            secretBytes.fill(0);

            return jsonResponse({
                code,
                remainingSeconds,
                periodSeconds: period
            });
        }

        // 6. POST /api/shares/:grantId/batch -> Obtener lote temporal precalculado (solo si OFFLINE_BATCH)
        if (method === "POST" && path.endsWith("/batch") && path.startsWith("/api/shares/")) {
            const grantId = decodeURIComponent(
                path.substring("/api/shares/".length, path.length - "/batch".length)
            );

            const row = (await env.APPOPT_SHARES_DB.prepare(
                `SELECT s.encrypted_secret_b64, s.iv_b64, s.algorithm, s.digits, s.period,
                        g.access_mode, g.batch_hours
                 FROM share_grants g
                 JOIN shared_services s ON g.service_id = s.service_id
                 WHERE g.grant_id = ? AND g.guest_email = ?`
            )
                .bind(grantId, callerEmail)
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
