package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudMacro
import br.com.augusto.jmud.util.ShareFormat

class MacroRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveMacros(macros: List<MudMacro>) {
        prefs.edit().putString(ShareFormat.KEY_MACROS, ShareFormat.macrosToText(macros)).apply()
    }

    fun loadMacros(): List<MudMacro> =
        ShareFormat.macrosFromText(prefs.getString(ShareFormat.KEY_MACROS, null))
}
