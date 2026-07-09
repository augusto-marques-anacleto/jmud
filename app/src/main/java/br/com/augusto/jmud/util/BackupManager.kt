package br.com.augusto.jmud.util

import android.content.Context
import org.json.JSONObject

class BackupManager(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun exportToJson(): String {
        val data = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                else -> continue
            }
            data.put(key, entry)
        }
        val root = JSONObject()
        root.put("app", "jMud")
        root.put("backupVersion", 1)
        root.put("data", data)
        return root.toString(2)
    }

    fun importFromJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "jMud") return false
            val data = root.getJSONObject("data")
            val editor = prefs.edit()
            for (key in data.keys()) {
                val entry = data.getJSONObject(key)
                when (entry.optString("t")) {
                    "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                    "i" -> editor.putInt(key, entry.getInt("v"))
                    "l" -> editor.putLong(key, entry.getLong("v"))
                    "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                    "s" -> editor.putString(key, entry.getString("v"))
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
