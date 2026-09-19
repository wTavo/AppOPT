package com.example.appopt.ui.screens.settings.coordinator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.BatteryOptimizationHelper

/**
 * Estado y coordinador de permisos del sistema para la pantalla de ajustes (Directivas 8 y 29).
 *
 * Encapsula la verificación inicial síncrona, la reactividad ante cambios de ciclo de vida (`ON_RESUME`)
 * y la invocación de launchers para cámara, notificaciones y optimización de batería.
 *
 * @param context Contexto de la aplicación.
 */
@Stable
class SettingsPermissionsState(
    private val context: Context,
    private val cameraLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val notificationLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val batteryLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    initialCamera: Boolean,
    initialNotification: Boolean,
    initialBattery: Boolean,
    private val onSuccessHaptic: () -> Unit
) {
    var isCameraGranted by mutableStateOf(initialCamera)
        internal set

    var isNotificationGranted by mutableStateOf(initialNotification)
        internal set

    var isBatteryOptimizationIgnored by mutableStateOf(initialBattery)
        internal set

    fun refreshPermissions() {
        isCameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        isBatteryOptimizationIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    fun requestCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun requestBatteryOptimization() {
        try {
            batteryLauncher.launch(
                BatteryOptimizationHelper.createIgnoreBatteryOptimizationIntent(context)
            )
        } catch (_: Exception) {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
        }
    }

    internal fun onBatteryResult() {
        isBatteryOptimizationIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        if (isBatteryOptimizationIgnored) {
            onSuccessHaptic()
        }
    }
}

/**
 * Recuerda e inicializa una instancia de [SettingsPermissionsState] con sincronización de ciclo de vida.
 */
@Composable
fun rememberSettingsPermissionsState(): SettingsPermissionsState {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()

    val initialCamera = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val initialNotification = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    val initialBattery = remember {
        BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    var stateRef: SettingsPermissionsState? by remember { mutableStateOf(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> stateRef?.isCameraGranted = granted }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> stateRef?.isNotificationGranted = granted }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        stateRef?.onBatteryResult()
    }

    val state = remember {
        SettingsPermissionsState(
            context = context,
            cameraLauncher = cameraLauncher,
            notificationLauncher = notificationLauncher,
            batteryLauncher = batteryLauncher,
            initialCamera = initialCamera,
            initialNotification = initialNotification,
            initialBattery = initialBattery,
            onSuccessHaptic = { appHaptics.success() }
        ).also { stateRef = it }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
