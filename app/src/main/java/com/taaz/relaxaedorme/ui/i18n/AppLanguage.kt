package com.taaz.relaxaedorme.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.taaz.relaxaedorme.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Idiomas oficiais suportados pelo aplicativo Relaxa e Dorme.
 * Português brasileiro é o idioma padrão em novas instalações.
 */
enum class AppLanguage(
    val code: String,
    val languageTag: String,
    val flagEmoji: String,
    val shortCode: String,
    val locale: Locale,
    @StringRes val accessibilityNameRes: Int
) {
    PT_BR(
        code = "pt-BR",
        languageTag = "pt-BR",
        flagEmoji = "🇧🇷",
        shortCode = "PT",
        locale = Locale.forLanguageTag("pt-BR"),
        accessibilityNameRes = R.string.language_portuguese
    ),
    EN(
        code = "en",
        languageTag = "en",
        flagEmoji = "🇺🇸",
        shortCode = "EN",
        locale = Locale.ENGLISH,
        accessibilityNameRes = R.string.language_english
    ),
    ES(
        code = "es",
        languageTag = "es",
        flagEmoji = "🇪🇸",
        shortCode = "ES",
        locale = Locale.forLanguageTag("es"),
        accessibilityNameRes = R.string.language_spanish
    );

    companion object {
        val DEFAULT: AppLanguage = PT_BR

        fun fromCode(code: String?): AppLanguage {
            val normalized = code?.trim().orEmpty()
            return entries.firstOrNull {
                it.code.equals(normalized, ignoreCase = true) ||
                    it.shortCode.equals(normalized, ignoreCase = true) ||
                    it.locale.language.equals(normalized.substringBefore("-"), ignoreCase = true)
            } ?: DEFAULT
        }
    }
}

/**
 * Cria um [Context] com a configuração regional aplicada para [language], permitindo troca
 * imediata de idioma em tempo de execução a partir da API 26+ sem reiniciar a Activity.
 */
fun createLocalizedContext(baseContext: Context, language: AppLanguage): Context {
    val configuration = Configuration(baseContext.resources.configuration)
    configuration.setLocale(language.locale)
    return baseContext.createConfigurationContext(configuration)
}

/**
 * Formata uma duração em milissegundos usando recursos plurais nativos do Android
 * (ex: "1 minuto", "12 minutos", "2 horas e 30 minutos").
 */
fun formatLocalizedDurationVerbose(context: Context, millis: Long): String {
    val safeMs = millis.coerceAtLeast(0L)
    val totalMinutes = safeMs / 60_000L
    val hours = (totalMinutes / 60L).toInt()
    val minutes = (totalMinutes % 60L).toInt()

    return when {
        hours <= 0 -> context.resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
        minutes == 0 -> context.resources.getQuantityString(R.plurals.duration_hours, hours, hours)
        else -> {
            val hoursText = context.resources.getQuantityString(R.plurals.duration_hours, hours, hours)
            val minutesText = context.resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
            context.getString(R.string.duration_hours_and_minutes, hoursText, minutesText)
        }
    }
}

/**
 * Formata o tempo acumulado de audição de forma compacta e localizada
 * (ex: "42 min", "12h 35min", "128h 42min").
 */
fun formatLocalizedListeningStats(
    context: Context,
    millis: Long,
    locale: Locale
): String {
    val safeMs = millis.coerceAtLeast(0L)
    val totalMinutes = safeMs / 60_000L
    val numberFormat = NumberFormat.getIntegerInstance(locale)
    if (totalMinutes < 60L) {
        return context.getString(
            R.string.stats_duration_minutes,
            numberFormat.format(totalMinutes)
        )
    }
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return context.getString(
        R.string.stats_duration_hours_minutes,
        numberFormat.format(hours),
        minutes
    )
}
