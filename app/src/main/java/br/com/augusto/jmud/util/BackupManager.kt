package br.com.augusto.jmud.util

import android.content.Context

class BackupManager(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun settingsSnapshot(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for ((key, value) in prefs.all) {
            if (key in ShareFormat.LIST_PREF_KEYS) continue
            if (value != null) {
                map[key] = value
            }
        }
        return map
    }

    fun applySettings(settings: Map<String, Any>) {
        if (settings.isEmpty()) return
        val editor = prefs.edit()
        for ((key, value) in settings) {
            if (key in ShareFormat.LIST_PREF_KEYS) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}
