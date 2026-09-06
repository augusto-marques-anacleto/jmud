package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudShortcut
import br.com.augusto.jmud.util.ShareFormat

class ShortcutRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveShortcuts(shortcuts: List<MudShortcut>) {
        prefs.edit().putString(ShareFormat.KEY_SHORTCUTS, ShareFormat.shortcutsToText(shortcuts)).apply()
    }

    fun loadShortcuts(): List<MudShortcut> =
        ShareFormat.shortcutsFromText(prefs.getString(ShareFormat.KEY_SHORTCUTS, null))
}
