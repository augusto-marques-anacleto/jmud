package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.util.ShareFormat

class TriggerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveTriggers(triggers: List<MudTrigger>) {
        prefs.edit().putString(ShareFormat.KEY_TRIGGERS, ShareFormat.triggersToText(triggers)).apply()
    }

    fun loadTriggers(): List<MudTrigger> =
        ShareFormat.triggersFromText(prefs.getString(ShareFormat.KEY_TRIGGERS, null))
}
