package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.util.ShareFormat

class CharacterRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveCharacters(characters: List<MudCharacter>) {
        prefs.edit().putString(ShareFormat.KEY_CHARACTERS, ShareFormat.charactersToText(characters)).apply()
    }

    fun loadCharacters(): List<MudCharacter> =
        ShareFormat.charactersFromText(prefs.getString(ShareFormat.KEY_CHARACTERS, null))

    fun saveManualConnection(
        host: String,
        port: String,
        useTTS: Boolean,
        playSounds: Boolean,
        autoReconnect: Boolean
    ) {
        prefs.edit()
            .putString("manual_host", host)
            .putString("manual_port", port)
            .putBoolean("manual_useTTS", useTTS)
            .putBoolean("manual_playSounds", playSounds)
            .putBoolean("manual_autoReconnect", autoReconnect)
            .apply()
    }

    fun getManualHost(): String = prefs.getString("manual_host", "") ?: ""
    fun getManualPort(): String = prefs.getString("manual_port", "") ?: ""
    fun getManualUseTTS(): Boolean = prefs.getBoolean("manual_useTTS", true)
    fun getManualPlaySounds(): Boolean = prefs.getBoolean("manual_playSounds", true)
    fun getManualAutoReconnect(): Boolean = prefs.getBoolean("manual_autoReconnect", false)
}