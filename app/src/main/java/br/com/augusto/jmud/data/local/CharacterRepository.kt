package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudCharacter
import org.json.JSONArray
import org.json.JSONObject

class CharacterRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveCharacters(characters: List<MudCharacter>) {
        val jsonArray = JSONArray()
        for (c in characters) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("host", c.host)
            obj.put("port", c.port)
            obj.put("password", c.password)
            obj.put("autoLogin", c.autoLogin)
            obj.put("postConnectCommands", c.postConnectCommands)
            obj.put("useTTS", c.useTTS)
            obj.put("playSounds", c.playSounds)
            obj.put("soundsFolder", c.soundsFolder)
            jsonArray.put(obj)
        }
        prefs.edit().putString("characters_list", jsonArray.toString()).apply()
    }

    fun loadCharacters(): List<MudCharacter> {
        val list = mutableListOf<MudCharacter>()
        val data = prefs.getString("characters_list", null) ?: return list
        try {
            val jsonArray = JSONArray(data)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MudCharacter(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        host = obj.getString("host"),
                        port = obj.getInt("port"),
                        password = obj.optString("password", ""),
                        autoLogin = obj.optBoolean("autoLogin", false),
                        postConnectCommands = obj.optString("postConnectCommands", ""),
                        useTTS = obj.optBoolean("useTTS", true),
                        playSounds = obj.optBoolean("playSounds", true),
                        soundsFolder = obj.optString("soundsFolder", "")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return list
    }

    fun saveManualConnection(host: String, port: String, useTTS: Boolean, playSounds: Boolean) {
        prefs.edit()
            .putString("manual_host", host)
            .putString("manual_port", port)
            .putBoolean("manual_useTTS", useTTS)
            .putBoolean("manual_playSounds", playSounds)
            .apply()
    }

    fun getManualHost(): String = prefs.getString("manual_host", "") ?: ""
    fun getManualPort(): String = prefs.getString("manual_port", "") ?: ""
    fun getManualUseTTS(): Boolean = prefs.getBoolean("manual_useTTS", true)
    fun getManualPlaySounds(): Boolean = prefs.getBoolean("manual_playSounds", true)
}