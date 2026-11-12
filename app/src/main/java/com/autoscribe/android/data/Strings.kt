package com.autoscribe.android.data

import java.util.Locale

/**
 * String lookup, mirroring desktop i18n.t(): the requested language, falling
 * back to English, with {placeholder} substitution. The desktop vis() RTL
 * reordering hack is NOT needed here — Android shapes Persian natively, so
 * strings are used exactly as stored.
 */
class Strings(val lang: String) {
    fun t(key: String, vararg args: Pair<String, Any>): String {
        val entry = STRING_TABLE[key] ?: return key
        var text = entry[lang] ?: entry["en"] ?: key
        for ((k, v) in args) {
            text = text.replace("{$k}", v.toString())
        }
        return text
    }

    fun region(region: String): String =
        REGION_NAMES[region]?.get(lang) ?: region

    /** Desktop i18n.num(): thousands separators, non-breaking space for Russian. */
    fun num(n: Long): String {
        // Locale.US: grouping is always "," here, exactly like Python f"{n:,}",
        // regardless of the device locale.
        val grouped = "%,d".format(Locale.US, n)
        return if (lang == "ru") grouped.replace(",", "\u00a0") else grouped
    }

    fun num(n: Int): String = num(n.toLong())

    /** Desktop i18n.duration(). */
    fun duration(seconds: Int): String {
        val s = maxOf(0, seconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val rem = s % 60
        return when {
            h > 0 -> t("dur_h", "h" to h, "m" to "%02d".format(m))
            m > 0 -> t("dur_m", "m" to m)
            else -> t("dur_s", "s" to rem)
        }
    }

    companion object {
        const val LANG_EN = "en"
        const val LANG_FA = "fa"
        const val LANG_RU = "ru"
        val LANGUAGES: Map<String, String> = mapOf(
            LANG_EN to "English",
            LANG_FA to "فارسی",
            LANG_RU to "Русский",
        )

        fun isRtl(lang: String): Boolean = lang == LANG_FA
    }
}
