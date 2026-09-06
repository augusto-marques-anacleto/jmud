package br.com.augusto.jmud.data.local

import android.content.Context
import android.provider.Settings
import br.com.augusto.jmud.util.IntervalFormat
import br.com.augusto.jmud.util.QuitCommands
import br.com.augusto.jmud.util.TiltZones

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

    fun saveCommandSeparator(value: String) {
        prefs.edit().putString("command_separator", value).apply()
    }

    fun getCommandSeparator(): String = prefs.getString("command_separator", " @ ") ?: " @ "

    fun saveCommandIntervalMs(value: Int) {
        prefs.edit().putInt("command_interval_ms", value).apply()
    }

    fun getCommandIntervalMs(): Int =
        prefs.getInt("command_interval_ms", IntervalFormat.DEFAULT_INTERVAL_MS)
            .coerceIn(0, IntervalFormat.MAX_INTERVAL_MS)

    fun saveShortcutPanelEnabled(value: Boolean) {
        prefs.edit().putBoolean("shortcut_panel_enabled", value).apply()
    }

    fun getShortcutPanelEnabled(): Boolean = prefs.getBoolean("shortcut_panel_enabled", false)

    fun saveTiltTriggerDegrees(value: Int) {
        prefs.edit().putInt("tilt_trigger_degrees", value).apply()
    }

    fun getTiltTriggerDegrees(): Int =
        prefs.getInt("tilt_trigger_degrees", TiltZones.DEFAULT_TRIGGER_DEGREES).coerceIn(5, 60)

    fun saveTiltConfirmMs(value: Int) {
        prefs.edit().putInt("tilt_confirm_ms", value).apply()
    }

    fun getTiltConfirmMs(): Int =
        prefs.getInt("tilt_confirm_ms", TiltZones.DEFAULT_CONFIRM_MS).coerceIn(100, 3000)

    fun saveTiltRepeatEnabled(value: Boolean) {
        prefs.edit().putBoolean("tilt_repeat_enabled", value).apply()
    }

    fun getTiltRepeatEnabled(): Boolean = prefs.getBoolean("tilt_repeat_enabled", true)

    fun saveTiltShortcut(zone: String, shortcutId: String) {
        prefs.edit().putString("tilt_zone_" + zone, shortcutId).apply()
    }

    fun getTiltShortcut(zone: String): String = prefs.getString("tilt_zone_" + zone, "") ?: ""

    fun saveQuitCommands(value: String) {
        prefs.edit().putString("quit_commands", value).apply()
    }

    fun getQuitCommands(): String =
        prefs.getString("quit_commands", QuitCommands.DEFAULT) ?: QuitCommands.DEFAULT

    fun saveWelcomeShown() {
        prefs.edit().putBoolean("welcome_shown", true).apply()
    }

    fun getWelcomeShown(): Boolean = prefs.getBoolean("welcome_shown", false)
}
