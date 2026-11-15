package com.taaz.relaxaedorme.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.taaz.relaxaedorme.data.model.SavedScene
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "relaxa_e_dorme_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Repositório de persistência local offline utilizando Jetpack DataStore e Kotlinx Serialization.
 *
 * Responsável por salvar e recuperar de forma independente:
 * - Favoritos musicais ([favoriteMusicIds]).
 * - Histórico recente de reprodução musical ([recentMusicIds]).
 * - Cenas personalizadas do Mixer ([savedScenes]).
 *
 * Segurança de migração:
 * - Atualizações do catálogo remoto nunca apagam dados persistidos do usuário.
 * - IDs antigos ou ainda não carregados no catálogo são preservados no armazenamento e
 *   filtrados com segurança na camada de apresentação sem quebrar a aplicação.
 */
class FavoritesRepository(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Fluxo seguro de leitura do DataStore que recupera graciosamente de falhas de I/O ([IOException])
     * emitindo preferências vazias, sem derrubar o aplicativo e sem ocultar outras exceções inesperadas.
     */
    private val safePreferencesFlow: Flow<Preferences> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    /**
     * Conjunto de IDs de músicas favoritadas pelo usuário.
     */
    val favoriteMusicIds: Flow<Set<String>> = safePreferencesFlow.map { preferences ->
        val rawJson = preferences[KEY_FAVORITE_MUSIC_IDS_JSON]
        if (rawJson.isNullOrBlank()) {
            emptySet()
        } else {
            runCatching {
                json.decodeFromString<Set<String>>(rawJson)
            }.getOrDefault(emptySet())
        }
    }

    /**
     * Cenas personalizadas do Mixer salvas localmente pelo usuário.
     */
    val savedScenes: Flow<List<SavedScene>> = safePreferencesFlow.map { preferences ->
        val rawJson = preferences[KEY_SAVED_SCENES_JSON]
        if (rawJson.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString<List<SavedScene>>(rawJson)
            }.getOrDefault(emptyList())
        }
    }

    /**
     * Lista ordenada (da mais recente para a mais antiga) dos IDs das últimas músicas reproduzidas.
     */
    val recentMusicIds: Flow<List<String>> = safePreferencesFlow.map { preferences ->
        val rawJson = preferences[KEY_RECENT_MUSIC_IDS_JSON]
        if (rawJson.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString<List<String>>(rawJson)
            }.getOrDefault(emptyList())
        }
    }

    val appLanguageCode: Flow<String> = safePreferencesFlow.map { preferences ->
        preferences[KEY_APP_LANGUAGE] ?: DEFAULT_LANGUAGE_CODE
    }

    /**
     * Indica se a última sessão de descanso foi encerrada pelo temporizador de sono
     * e ainda aguarda o retorno do usuário para o estado de despertar da Ghosty Buddy.
     */
    val pendingWakeUpFromSleep: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        preferences[KEY_PENDING_WAKE_UP_FROM_SLEEP] ?: false
    }

    suspend fun toggleFavoriteMusic(trackId: String) {
        if (trackId.isBlank()) return
        context.appPreferencesDataStore.edit { preferences ->
            val currentSet = preferences[KEY_FAVORITE_MUSIC_IDS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
            } ?: emptySet()

            val updatedSet = if (trackId in currentSet) {
                currentSet - trackId
            } else {
                currentSet + trackId
            }
            preferences[KEY_FAVORITE_MUSIC_IDS_JSON] = json.encodeToString(updatedSet)
        }
    }

    suspend fun recordRecentMusic(trackId: String) {
        if (trackId.isBlank()) return
        context.appPreferencesDataStore.edit { preferences ->
            val currentList = preferences[KEY_RECENT_MUSIC_IDS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()

            if (currentList.firstOrNull() == trackId) {
                return@edit
            }

            val updatedList = (listOf(trackId) + currentList.filterNot { it == trackId })
                .take(MAX_RECENT_TRACKS)

            preferences[KEY_RECENT_MUSIC_IDS_JSON] = json.encodeToString(updatedList)
        }
    }

    suspend fun saveScene(scene: SavedScene) {
        context.appPreferencesDataStore.edit { preferences ->
            val currentList = preferences[KEY_SAVED_SCENES_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<SavedScene>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()

            val updatedList = currentList
                .filterNot { it.id == scene.id } + scene

            preferences[KEY_SAVED_SCENES_JSON] = json.encodeToString(updatedList)
        }
    }

    suspend fun deleteScene(sceneId: String) {
        context.appPreferencesDataStore.edit { preferences ->
            val currentList = preferences[KEY_SAVED_SCENES_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<SavedScene>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()

            val updatedList = currentList.filterNot { it.id == sceneId }
            preferences[KEY_SAVED_SCENES_JSON] = json.encodeToString(updatedList)
        }
    }

    suspend fun markSleepEndedByTimer() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[KEY_PENDING_WAKE_UP_FROM_SLEEP] = true
        }
    }

    suspend fun clearPendingWakeUpFromSleep() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences.remove(KEY_PENDING_WAKE_UP_FROM_SLEEP)
        }
    }

    suspend fun setAppLanguageCode(languageCode: String) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[KEY_APP_LANGUAGE] = languageCode
        }
    }

    companion object {
        private const val MAX_RECENT_TRACKS = 8
        const val DEFAULT_LANGUAGE_CODE = "pt-BR"
        private val KEY_FAVORITE_MUSIC_IDS_JSON = stringPreferencesKey("favorite_music_ids_json")
        private val KEY_SAVED_SCENES_JSON = stringPreferencesKey("saved_scenes_json")
        private val KEY_RECENT_MUSIC_IDS_JSON = stringPreferencesKey("recent_music_ids_json")
        private val KEY_PENDING_WAKE_UP_FROM_SLEEP = booleanPreferencesKey("pending_wake_up_from_sleep")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}
