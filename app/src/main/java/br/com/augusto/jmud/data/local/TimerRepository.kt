package br.com.augusto.jmud.data.local

import android.content.Context
import br.com.augusto.jmud.domain.MudTimer
import br.com.augusto.jmud.util.ShareFormat

class TimerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cmud_data", Context.MODE_PRIVATE)

    fun saveTimers(timers: List<MudTimer>) {
        prefs.edit().putString(ShareFormat.KEY_TIMERS, ShareFormat.timersToText(timers)).apply()
    }

    fun loadTimers(): List<MudTimer> =
        ShareFormat.timersFromText(prefs.getString(ShareFormat.KEY_TIMERS, null))
}
