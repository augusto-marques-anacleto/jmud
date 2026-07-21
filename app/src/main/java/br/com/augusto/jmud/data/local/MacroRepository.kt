package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudMacro
import br.com.augusto.jmud.domain.Scope
import org.json.JSONArray
import org.json.JSONObject

class MacroRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveMacros(macros: List<MudMacro>) {
        val jsonArray = JSONArray()
        for (m in macros) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("commands", m.commands)
            obj.put("scope", m.scope)
            obj.put("scopeValue", m.scopeValue)
            obj.put("enabled", m.enabled)
            jsonArray.put(obj)
        }
        prefs.edit().putString("macros_list", jsonArray.toString()).apply()
    }

    fun loadMacros(): List<MudMacro> {
        val list = mutableListOf<MudMacro>()
        val data = prefs.getString("macros_list", null) ?: return list
        try {
            val jsonArray = JSONArray(data)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MudMacro(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        commands = obj.optString("commands", ""),
                        scope = obj.optString("scope", Scope.ALL),
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
