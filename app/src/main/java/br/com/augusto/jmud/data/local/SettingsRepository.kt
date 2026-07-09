package br.com.augusto.jmud.data.local

import android.content.Context
import android.provider.Settings

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver

    fun saveEncoding(value: String) {
        prefs.edit().putString("encoding", value).apply()
    }

    fun getEncoding(): String = prefs.getString("encoding", "ISO-8859-1") ?: "ISO-8859-1"

    fun saveTtsEngine(value: String) {
        prefs.edit().putString("tts_engine", value).apply()
    }

    fun getTtsEngine(): String = prefs.getString("tts_engine", "") ?: ""

    fun saveTtsVoice(value: String) {
        prefs.edit().putString("tts_voice", value).apply()
    }

    fun getTtsVoice(): String = prefs.getString("tts_voice", "") ?: ""

    fun saveTtsRate(value: Float) {
        prefs.edit().putFloat("tts_rate", value).apply()
    }

    fun getTtsRate(): Float {
        if (prefs.contains("tts_rate")) {
            return prefs.getFloat("tts_rate", 1.0f)
        }
        return getSystemTtsRate()
    }

    fun getSystemTtsRate(): Float =
        Settings.Secure.getInt(resolver, "tts_default_rate", 100) / 100f

    fun saveTtsPitch(value: Float) {
        prefs.edit().putFloat("tts_pitch", value).apply()
    }

    fun getTtsPitch(): Float {
        if (prefs.contains("tts_pitch")) {
            return prefs.getFloat("tts_pitch", 1.0f)
        }
        return getSystemTtsPitch()
    }

    fun getSystemTtsPitch(): Float =
        Settings.Secure.getInt(resolver, "tts_default_pitch", 100) / 100f

    fun clearTtsRateAndPitch() {
        prefs.edit().remove("tts_rate").remove("tts_pitch").apply()
    }

    fun saveTtsVolume(value: Float) {
        prefs.edit().putFloat("tts_volume", value).apply()
    }

    fun getTtsVolume(): Float = prefs.getFloat("tts_volume", 1.0f)

    fun saveLogsEnabled(value: Boolean) {
        prefs.edit().putBoolean("logs_enabled", value).apply()
    }

    fun getLogsEnabled(): Boolean = prefs.getBoolean("logs_enabled", true)

    fun saveLogRetentionDays(value: Int) {
        prefs.edit().putInt("log_retention_days", value).apply()
    }

    fun getLogRetentionDays(): Int = prefs.getInt("log_retention_days", 30)

    fun saveTriggersEnabled(value: Boolean) {
        prefs.edit().putBoolean("triggers_enabled", value).apply()
    }

    fun getTriggersEnabled(): Boolean = prefs.getBoolean("triggers_enabled", true)

    fun saveTimersEnabled(value: Boolean) {
        prefs.edit().putBoolean("timers_enabled", value).apply()
    }

    fun getTimersEnabled(): Boolean = prefs.getBoolean("timers_enabled", true)
}
