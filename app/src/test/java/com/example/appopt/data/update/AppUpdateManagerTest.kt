package com.example.appopt.data.update

import com.example.appopt.domain.model.AppUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias determinísticas para el motor de actualización integrada [AppUpdateManager] y [AppUpdateInfo] (Directiva 16).
 */
class AppUpdateManagerTest {

    /**
     * Verifica la comparación semántica de versiones (`MAJOR.MINOR.PATCH`) incluyendo prefijos `v` y sufijos `-debug`.
     */
    @Test
    fun `AppUpdateInfo isRemoteVersionNewer compares semantic versions accurately`() {
        assertFalse(AppUpdateInfo.isRemoteVersionNewer("1.0.0", "v1.0.0"))
        assertFalse(AppUpdateInfo.isRemoteVersionNewer("1.0", "1.0.0"))
        assertFalse(AppUpdateInfo.isRemoteVersionNewer("1.2.0", "v1.1.9"))
        assertTrue(AppUpdateInfo.isRemoteVersionNewer("1.0.0-debug", "v1.0.1"))
        assertTrue(AppUpdateInfo.isRemoteVersionNewer("1.0.0", "v1.1.0"))
        assertTrue(AppUpdateInfo.isRemoteVersionNewer("1.9.9", "v2.0.0"))
    }

    /**
     * Verifica el parseo del JSON de GitHub Releases extrayendo el activo `.apk` y el digest `sha256:` oficial.
     */
    @Test
    fun `AppUpdateManager parseLatestReleaseJson extracts apk asset and sha256 digest`() {
        val sampleDigest = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val json = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "body": "Mejoras de rendimiento y seguridad Zero-Secret.",
              "assets": [
                {
                  "name": "app-release.apk",
                  "size": 7340032,
                  "digest": "sha256:$sampleDigest",
                  "browser_download_url": "https://github.com/wTavo/AppOPT/releases/download/v1.2.0/app-release.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateManager.parseLatestReleaseJson(json)
        assertNotNull(parsed)
        assertEquals("v1.2.0", parsed!!.tagName)
        assertEquals("1.2.0", parsed.versionName)
        assertEquals("Mejoras de rendimiento y seguridad Zero-Secret.", parsed.releaseNotes)
        assertEquals("https://github.com/wTavo/AppOPT/releases/download/v1.2.0/app-release.apk", parsed.apkDownloadUrl)
        assertEquals(7340032L, parsed.apkSizeBytes)
        assertEquals(sampleDigest, parsed.expectedSha256)
        assertEquals("7.0 MB", parsed.formattedSizeMb())
    }

    /**
     * Verifica que borradores (`draft = true`) o lanzamientos sin archivos `.apk` retornen `null`.
     */
    @Test
    fun `AppUpdateManager parseLatestReleaseJson ignores drafts and releases without apk`() {
        val draftJson = """
            {
              "tag_name": "v1.3.0",
              "draft": true,
              "assets": [
                {
                  "name": "app-release.apk",
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()

        assertNull(AppUpdateManager.parseLatestReleaseJson(draftJson))
    }

    /**
     * Verifica que getFormattedReleaseNotes limpie hashes SHA-256, títulos redundantes y formatee viñetas limpiamente.
     */
    @Test
    fun `AppUpdateInfo getFormattedReleaseNotes strips sha256 and formats clean bullets`() {
        val rawBody = """
            ## AppOPT Authenticator v1.0.1
            ### Novedades:
            - **Seguridad**: Cifrado mejorado de claves.
            - **Interfaz**: Navegación fluida.
            
            ### Verificación de Integridad (SHA-256)
            `912022b605c9999298040db3ddf7558213a78da009da37f308161a36ad3560be`
        """.trimIndent()

        val info = AppUpdateInfo(
            tagName = "v1.0.1",
            versionName = "1.0.1",
            releaseNotes = rawBody,
            apkDownloadUrl = "https://example.com/app.apk",
            apkSizeBytes = 1024L,
            expectedSha256 = "912022b605c9999298040db3ddf7558213a78da009da37f308161a36ad3560be"
        )

        val notes = info.getFormattedReleaseNotes()
        assertEquals(3, notes.size)
        assertEquals("Novedades:", notes[0])
        assertEquals("• Seguridad: Cifrado mejorado de claves.", notes[1])
        assertEquals("• Interfaz: Navegación fluida.", notes[2])
    }
}
