package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "winrah_prefs")

/** Minutes after local midnight, e.g. 420 = 7:00. */
data class Commute(
    val fromStationId: String,
    val toStationId: String,
    val outboundMinute: Int,
    val returnMinute: Int,
)

data class Prefs(
    val onboarded: Boolean = false,
    val favoriteStationIds: List<String> = emptyList(),
    val commute: Commute? = null,
    val alertStationId: String? = null,
    val alertLeadMinutes: Int = 5,
    val soundId: String = "electric",
    val alarmSoundId: String = "steam",
    val soundVolume: Int = 80,
    val vibrate: Boolean = true,
    val theme: String = "system",       // system | light | dark
    val language: String = "ar",        // ar | fr | en
    val supporterUntilMs: Long = 0L,    // entitlement cache; Play Billing is the truth
    val lifetimeSupporter: Boolean = false,
) {
    val isSupporter: Boolean get() = lifetimeSupporter || supporterUntilMs > System.currentTimeMillis()

    /** Free tier limits shown in the support screen. */
    val maxFavorites: Int get() = if (isSupporter) Int.MAX_VALUE else FREE_FAVORITES

    companion object { const val FREE_FAVORITES = 3 }
}

class UserPrefsRepository(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val favorites = stringPreferencesKey("favorite_station_ids")
        val commuteFrom = stringPreferencesKey("commute_from")
        val commuteTo = stringPreferencesKey("commute_to")
        val commuteOut = intPreferencesKey("commute_out_min")
        val commuteBack = intPreferencesKey("commute_back_min")
        val alertStation = stringPreferencesKey("alert_station")
        val alertLead = intPreferencesKey("alert_lead_min")
        val sound = stringPreferencesKey("sound_id")
        val alarmSound = stringPreferencesKey("alarm_sound_id")
        val volume = intPreferencesKey("sound_volume")
        val vibrate = booleanPreferencesKey("vibrate")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val supporterUntil = stringPreferencesKey("supporter_until_ms")
        val lifetime = booleanPreferencesKey("lifetime_supporter")
    }

    val prefs: Flow<Prefs> = context.store.data.map { p ->
        val from = p[K.commuteFrom]
        val to = p[K.commuteTo]
        Prefs(
            onboarded = p[K.onboarded] ?: false,
            favoriteStationIds = p[K.favorites]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            commute = if (from != null && to != null) {
                Commute(from, to, p[K.commuteOut] ?: 420, p[K.commuteBack] ?: 1020)
            } else null,
            alertStationId = p[K.alertStation],
            alertLeadMinutes = p[K.alertLead] ?: 5,
            soundId = p[K.sound] ?: "electric",
            alarmSoundId = p[K.alarmSound] ?: "steam",
            soundVolume = p[K.volume] ?: 80,
            vibrate = p[K.vibrate] ?: true,
            theme = p[K.theme] ?: "system",
            language = p[K.language] ?: "ar",
            supporterUntilMs = p[K.supporterUntil]?.toLongOrNull() ?: 0L,
            lifetimeSupporter = p[K.lifetime] ?: false,
        )
    }

    suspend fun setOnboarded() = context.store.edit { it[K.onboarded] = true }

    suspend fun setFavorites(ids: List<String>) = context.store.edit { it[K.favorites] = ids.distinct().joinToString(",") }

    suspend fun setCommute(c: Commute?) = context.store.edit {
        if (c == null) {
            it.remove(K.commuteFrom); it.remove(K.commuteTo); it.remove(K.commuteOut); it.remove(K.commuteBack)
        } else {
            it[K.commuteFrom] = c.fromStationId; it[K.commuteTo] = c.toStationId
            it[K.commuteOut] = c.outboundMinute; it[K.commuteBack] = c.returnMinute
        }
    }

    suspend fun setAlert(stationId: String?, leadMinutes: Int = 5) = context.store.edit {
        if (stationId == null) it.remove(K.alertStation) else it[K.alertStation] = stationId
        it[K.alertLead] = leadMinutes
    }

    suspend fun setSound(id: String) = context.store.edit { it[K.sound] = id }
    suspend fun setAlarmSound(id: String) = context.store.edit { it[K.alarmSound] = id }
    suspend fun setVolume(v: Int) = context.store.edit { it[K.volume] = v.coerceIn(0, 100) }
    suspend fun setVibrate(v: Boolean) = context.store.edit { it[K.vibrate] = v }
    suspend fun setTheme(t: String) = context.store.edit { it[K.theme] = t }
    suspend fun setLanguage(l: String) = context.store.edit { it[K.language] = l }

    suspend fun setEntitlement(untilMs: Long, lifetime: Boolean) = context.store.edit {
        it[K.supporterUntil] = untilMs.toString(); it[K.lifetime] = lifetime
    }
}
