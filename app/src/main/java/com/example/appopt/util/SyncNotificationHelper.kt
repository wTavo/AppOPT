package com.example.appopt.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.appopt.MainActivity
import com.example.appopt.R

/**
 * Gestor centralizado de notificaciones del sistema para eventos de sincronización en la nube.
 *
 * Responsabilidades:
 * - Creación y configuración del canal de notificaciones del sistema ([CHANNEL_ID_CLOUD_SYNC]).
 * - Publicación de notificaciones de confirmación cuando una copia de seguridad en segundo plano
 *   se completa exitosamente (similar a la experiencia de aplicaciones de mensajería).
 * - Manejo seguro de permisos de notificación en Android 13+ (API 33+).
 */
object SyncNotificationHelper {

    /**
     * Identificador único del canal de notificaciones para sincronización en la nube.
     */
    const val CHANNEL_ID_CLOUD_SYNC = "appopt_cloud_vault_sync_channel"

    /**
     * Identificador numérico único de la notificación de sincronización.
     */
    const val NOTIFICATION_ID_SYNC_SUCCESS = 2001

    /**
     * Registra el canal de notificaciones para la sincronización en segundo plano en Android 8.0+ (API 26+).
     *
     * @param context Contexto de la aplicación.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_sync_name)
            val descriptionText = context.getString(R.string.notification_channel_sync_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(CHANNEL_ID_CLOUD_SYNC, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Muestra una notificación en la bandeja del sistema confirmando que la copia automática
     * en Google Drive se completó satisfactoriamente.
     *
     * @param context Contexto de la aplicación.
     * @param accountsCount Cantidad de servicios o credenciales 2FA respaldadas.
     */
    fun showSyncSuccessNotification(context: Context, accountsCount: Int = 0) {
        // Asegurar que el canal exista en sistemas Android O+
        createNotificationChannel(context)

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = context.getString(R.string.notification_sync_success_title)
            val contentText = context.getString(R.string.notification_sync_success_msg)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_CLOUD_SYNC)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(NOTIFICATION_ID_SYNC_SUCCESS, builder.build())
        } catch (_: SecurityException) {
            // Manejo defensivo en Android 13+ si el permiso POST_NOTIFICATIONS fue revocado
        } catch (_: Exception) {
            // Fallback silencioso sin exponer detalles técnicos a la vista
        }
    }
}
