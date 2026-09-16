package com.example.appopt.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.appopt.MainActivity
import com.example.appopt.R

/**
 * Gestor centralizado de notificaciones del sistema para el ciclo de vida de la sincronización en la nube.
 *
 * Responsabilidades:
 * - Creación y configuración del canal de notificaciones del sistema ([CHANNEL_ID_CLOUD_SYNC]).
 * - Emisión de notificaciones de estado en tiempo real:
 *   1. En curso ("Sincronizando con Google Drive…") con indicador de progreso continuo.
 *   2. Completada con éxito ("Copia de seguridad completada").
 *   3. Error de sincronización ("Error al sincronizar").
 * - Manejo seguro y defensivo de permisos de notificación en Android 13+ (API 33+).
 */
object SyncNotificationHelper {

    /**
     * Identificador único del canal de notificaciones para sincronización en la nube.
     */
    const val CHANNEL_ID_CLOUD_SYNC = "appopt_cloud_vault_sync_channel"

    /**
     * Identificador numérico único de la notificación de sincronización.
     */
    const val NOTIFICATION_ID_SYNC = 2001

    /**
     * Registra el canal de notificaciones para la sincronización en segundo plano en el sistema.
     *
     * @param context Contexto de la aplicación.
     */
    fun createNotificationChannel(context: Context) {
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

    /**
     * Muestra una notificación continua e interactiva indicando que la copia de seguridad está en curso.
     *
     * @param context Contexto de la aplicación.
     */
    fun showSyncProgressNotification(context: Context) {
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

            val title = context.getString(R.string.notification_sync_progress_title)
            val contentText = context.getString(R.string.notification_sync_progress_msg)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_CLOUD_SYNC)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentTitle(title)
                .setContentText(contentText)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)

            notificationManager.notify(NOTIFICATION_ID_SYNC, builder.build())
        } catch (_: SecurityException) {
            // Manejo defensivo en Android 13+ si el permiso fue revocado
        } catch (_: Exception) {
            // Fallback silencioso sin exponer detalles técnicos a la vista
        }
    }

    /**
     * Actualiza la notificación de sincronización a estado de éxito finalizado.
     *
     * @param context Contexto de la aplicación.
     */
    fun showSyncSuccessNotification(context: Context) {
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
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)

            notificationManager.notify(NOTIFICATION_ID_SYNC, builder.build())
        } catch (_: SecurityException) {
            // Manejo defensivo en Android 13+ si el permiso fue revocado
        } catch (_: Exception) {
            // Fallback silencioso sin exponer detalles técnicos a la vista
        }
    }

    /**
     * Actualiza la notificación de sincronización a estado de error o fallo de conexión.
     *
     * @param context Contexto de la aplicación.
     */
    fun showSyncFailureNotification(context: Context) {
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

            val title = context.getString(R.string.notification_sync_error_title)
            val contentText = context.getString(R.string.notification_sync_error_msg)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_CLOUD_SYNC)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentTitle(title)
                .setContentText(contentText)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)

            notificationManager.notify(NOTIFICATION_ID_SYNC, builder.build())
        } catch (_: SecurityException) {
            // Manejo defensivo en Android 13+ si el permiso fue revocado
        } catch (_: Exception) {
            // Fallback silencioso sin exponer detalles técnicos a la vista
        }
    }
}
