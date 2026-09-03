package com.example.appopt.util

import android.content.Context
import com.example.appopt.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formateador centralizado de marcas de tiempo y fechas (*Localization & Relative Time Formatting*).
 *
 * Directivas de desarrollo:
 * - PROHIBIDO instanciar `SimpleDateFormat` con cadenas mágicas de formato dispersas en los Composables.
 * - OBLIGATORIO consumir [DateTimeFormatter] para garantizar que los formatos respeten la configuración
 *   regional y el idioma del sistema del usuario.
 */
object DateTimeFormatter {

    /**
     * Formatea una marca de tiempo absoluta en el formato estándar localizado "dd/MM/yyyy HH:mm".
     *
     * @param timestamp Tiempo en milisegundos desde Unix Epoch.
     * @param locale Configuración regional (por defecto [Locale.getDefault]).
     * @return Cadena formateada de fecha y hora.
     */
    fun formatAbsoluteDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return ""
        val date = Date(timestamp)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)
        return format.format(date)
    }

    /**
     * Formatea una marca de tiempo de forma relativa e intuitiva (ej. "hace unos momentos", "hoy a las 14:30").
     *
     * @param context Contexto de la aplicación para resolver recursos traducidos de texto.
     * @param timestamp Tiempo en milisegundos desde Unix Epoch.
     * @param locale Configuración regional.
     * @return Cadena con la representación relativa del tiempo.
     */
    fun formatRelativeSyncTime(
        context: Context,
        timestamp: Long,
        locale: Locale = Locale.getDefault()
    ): String {
        if (timestamp <= 0L) return ""

        val now = System.currentTimeMillis()
        val diffMillis = now - timestamp

        if (diffMillis < 0L) {
            return formatAbsoluteDateTime(timestamp, locale)
        }

        val diffMinutes = diffMillis / (60 * 1000)
        val diffHours = diffMillis / (60 * 60 * 1000)

        val timeFormat = SimpleDateFormat("HH:mm", locale)
        val timeString = timeFormat.format(Date(timestamp))

        val calendarNow = Calendar.getInstance()
        val calendarTarget = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                calendarNow.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)

        val calendarYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calendarYesterday.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                calendarYesterday.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)

        return when {
            diffMinutes < 1 -> context.getString(R.string.time_just_now)
            diffMinutes < 60 -> context.getString(R.string.time_minutes_ago, diffMinutes)
            isSameDay -> context.getString(R.string.time_today_at, timeString)
            isYesterday -> context.getString(R.string.time_yesterday_at, timeString)
            else -> formatAbsoluteDateTime(timestamp, locale)
        }
    }
}
