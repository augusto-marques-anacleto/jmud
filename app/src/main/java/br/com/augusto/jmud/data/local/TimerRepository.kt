package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudTimer
import org.json.JSONArray
import org.json.JSONObject

class TimerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveTimers(timers: List<MudTimer>) {
        val jsonArray = JSONArray()
        for (t in timers) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("seconds", t.seconds)
            obj.put("commands", t.commands)
            obj.put("scope", t.scope)
            obj.put("scopeValue", t.scopeValue)
            obj.put("enabled", t.enabled)
            jsonArray.put(obj)
        }
        prefs.edit().putString("timers_list", jsonArray.toString()).apply()
    }

    fun loadTimers(): List<MudTimer> {
        val list = mutableListOf<MudTimer>()
        val data = prefs.getString("timers_list", null) ?: return list
        try {
            val jsonArray = JSONArray(data)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MudTimer(
                        id = obj.getString("id"),
                        seconds = obj.optInt("seconds", 60).coerceAtLeast(1),
                        commands = obj.optString("commands", ""),
                        scope = obj.optString("scope", MudTimer.SCOPE_ALL),
                        scopeValue = obj.optString("scopeValue", ""),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        } catch (e: Exception) {
        }
        return list
    }
}
