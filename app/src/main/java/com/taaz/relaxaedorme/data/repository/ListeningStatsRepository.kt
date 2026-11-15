package com.taaz.relaxaedorme.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.listeningStatsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ListeningStatsRepository.DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Snapshot imutável das estatísticas locais de audição acumuladas no dispositivo atual.
 */
data class ListeningStatsSnapshot(
    val totalMs: Long = 0L,
    val musicMs: Long = 0L,
    val ambientMs: Long = 0L
)

/**
 * Abstração de persistência local para permitir testes unitários determinísticos sem contexto Android.
 */
interface ListeningStatsStore {
    suspend fun loadSnapshot(): ListeningStatsSnapshot
    suspend fun saveSnapshot(snapshot: ListeningStatsSnapshot)
}

/**
 * Repositório 100% local de estatísticas de audição utilizando Jetpack DataStore dedicado
 * (`listening_stats_local.preferences_pb`), excluído das regras de backup em nuvem do Android.
 *
 * Nenhum dado é enviado para servidores externos, analytics ou sincronizado entre dispositivos.
 */
class ListeningStatsRepository(
    private val context: Context
) : ListeningStatsStore {

    override suspend fun loadSnapshot(): ListeningStatsSnapshot {
        val prefs = context.listeningStatsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                ListeningStatsSnapshot(
                    totalMs = (preferences[KEY_TOTAL_MS] ?: 0L).coerceAtLeast(0L),
                    musicMs = (preferences[KEY_MUSIC_MS] ?: 0L).coerceAtLeast(0L),
                    ambientMs = (preferences[KEY_AMBIENT_MS] ?: 0L).coerceAtLeast(0L)
                )
            }
            .first()
        return prefs
    }

    override suspend fun saveSnapshot(snapshot: ListeningStatsSnapshot) {
        context.listeningStatsDataStore.edit { preferences ->
            preferences[KEY_TOTAL_MS] = snapshot.totalMs.coerceAtLeast(0L)
            preferences[KEY_MUSIC_MS] = snapshot.musicMs.coerceAtLeast(0L)
            preferences[KEY_AMBIENT_MS] = snapshot.ambientMs.coerceAtLeast(0L)
        }
    }

    companion object {
        const val DATASTORE_NAME = "listening_stats_local"
        private val KEY_TOTAL_MS = longPreferencesKey("total_listening_ms")
        private val KEY_MUSIC_MS = longPreferencesKey("music_listening_ms")
        private val KEY_AMBIENT_MS = longPreferencesKey("ambient_listening_ms")
    }
}
