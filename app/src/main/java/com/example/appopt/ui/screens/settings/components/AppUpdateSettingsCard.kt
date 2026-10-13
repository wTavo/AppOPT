package com.example.appopt.ui.screens.settings.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.data.update.ApkIntegrityVerificationException
import com.example.appopt.data.update.AppUpdateManager
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.components.SettingsStatusTile
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import java.io.File

/**
 * Tarjeta de actualización automática para comprobar silenciosamente, descargar, verificar con SHA-256
 * e instalar nuevas versiones oficiales desde GitHub Releases (Directivas 1, 2, 3, 4, 5, 7, 9 y 15).
 *
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AppUpdateSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (installedVersionName, installedVersionCode) = remember(context) {
        AppUpdateManager.getInstalledVersion(context)
    }

    val availableUpdate by AppUpdateManager.availableUpdate.collectAsStateWithLifecycle()
    val currentUpdate = availableUpdate

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var verifiedApkFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (SharedServicesCloudClient.isNetworkAvailable(context)) {
            AppUpdateManager.checkForUpdate(context)
        }
    }

    SettingsSectionCard(
        title = stringResource(R.string.settings_update_card_title),
        description = stringResource(R.string.settings_update_card_desc),
        icon = Icons.Filled.SystemUpdate,
        iconTint = if (currentUpdate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        SettingsStatusTile(
            icon = if (currentUpdate != null) Icons.Filled.CloudDownload else Icons.Filled.CheckCircle,
            iconTint = if (currentUpdate != null) MaterialTheme.colorScheme.primary else SafeGreen,
            title = if (currentUpdate != null) {
                stringResource(
                    R.string.settings_update_status_available,
                    currentUpdate.tagName,
                    currentUpdate.formattedSizeMb()
                )
            } else {
                stringResource(R.string.settings_update_status_up_to_date)
            },
            subtitle = stringResource(
                R.string.settings_update_current_version,
                installedVersionName,
                installedVersionCode
            ),
            titleColor = if (currentUpdate != null) MaterialTheme.colorScheme.primary else SafeGreen
        )

        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
            exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
        ) {
            SettingsStatusTile(
                icon = Icons.Filled.Warning,
                iconTint = UrgentRed,
                title = errorMessage.orEmpty(),
                titleColor = UrgentRed
            )
        }

        AnimatedVisibility(
            visible = currentUpdate != null && currentUpdate.releaseNotes.isNotBlank(),
            enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
            exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_update_release_notes_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentUpdate?.releaseNotes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isDownloading,
            enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
            exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.settings_update_downloading_progress, downloadProgress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.ComponentHeight.progressIndicator)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.pill))
                )
            }
        }

        if (currentUpdate != null) {
            AppAnimatedButton(
                text = stringResource(R.string.settings_update_download_install_action, currentUpdate.tagName),
                leadingIcon = Icons.Filled.CloudDownload,
                enabled = !isDownloading,
                onClick = {
                    val readyFile = verifiedApkFile
                    if (readyFile != null && readyFile.exists()) {
                        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_update_permission_needed_msg),
                                Toast.LENGTH_LONG
                            ).show()
                            AppUpdateManager.openManageUnknownAppSourcesSettings(context)
                            return@AppAnimatedButton false
                        }
                        AppUpdateManager.launchApkInstaller(context, readyFile)
                        return@AppAnimatedButton true
                    }

                    if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                        errorMessage = context.getString(R.string.settings_update_error_no_internet)
                        return@AppAnimatedButton false
                    }

                    isDownloading = true
                    downloadProgress = 0
                    errorMessage = null

                    val downloadResult = AppUpdateManager.downloadAndVerifyApk(
                        context = context,
                        updateInfo = currentUpdate,
                        onProgress = { pct -> downloadProgress = pct }
                    )
                    isDownloading = false

                    downloadResult.fold(
                        onSuccess = { apkFile ->
                            verifiedApkFile = apkFile
                            if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_update_permission_needed_msg),
                                    Toast.LENGTH_LONG
                                ).show()
                                AppUpdateManager.openManageUnknownAppSourcesSettings(context)
                            } else {
                                AppUpdateManager.launchApkInstaller(context, apkFile)
                            }
                            true
                        },
                        onFailure = { err ->
                            errorMessage = when (err) {
                                is ApkIntegrityVerificationException ->
                                    context.getString(R.string.settings_update_error_hash_mismatch)
                                else ->
                                    context.getString(R.string.settings_update_error_generic)
                            }
                            false
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
