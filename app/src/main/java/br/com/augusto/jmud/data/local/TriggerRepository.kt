package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.Scope
import org.json.JSONArray
import org.json.JSONObject

class TriggerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveTriggers(triggers: List<MudTrigger>) {
        val jsonArray = JSONArray()
        for (t in triggers) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("message", t.message)
            obj.put("matchType", t.matchType)
            obj.put("commands", t.commands)
            obj.put("scope", t.scope)
            obj.put("scopeValue", t.scopeValue)
            obj.put("enabled", t.enabled)
            obj.put("ignoreLine", t.ignoreLine)
            obj.put("historyName", t.historyName)
            obj.put("soundName", t.soundName)
            jsonArray.put(obj)
        }
        prefs.edit().putString("triggers_list", jsonArray.toString()).apply()
    }

    fun loadTriggers(): List<MudTrigger> {
        val list = mutableListOf<MudTrigger>()
        val data = prefs.getString("triggers_list", null) ?: return list
        try {
            val jsonArray = JSONArray(data)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MudTrigger(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        message = obj.optString("message", ""),
                        matchType = obj.optString("matchType", MudTrigger.MATCH_START),
                        commands = obj.optString("commands", ""),
                        scope = obj.optString("scope", Scope.ALL),
                        scopeValue = obj.optString("scopeValue", ""),
                        enabled = obj.optBoolean("enabled", true),
                        ignoreLine = obj.optBoolean("ignoreLine", false),
                        historyName = obj.optString("historyName", ""),
                        soundName = obj.optString("soundName", "")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return list
    }
}
