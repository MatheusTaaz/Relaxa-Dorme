package com.taaz.relaxaedorme.data.remote

import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Registro explícito e imutável das 4 músicas oficiais de demonstração empacotadas
 * em `app/src/main/assets/demo_music/` e mapeamento determinístico das capas individuais
 * hospedadas no Cloudflare R2.
 *
 * Regras estritas:
 * - A associação dos 4 áudios locais ocorre exclusivamente por ID exato da faixa -> caminho exato do asset no APK.
 * - Não cria uma biblioteca local independente; apenas fornece o caminho do asset local
 *   quando a faixa correspondente é publicada no `catalog.json` remoto.
 */
object LocalDemoMusicAssets {

    const val TRACK_ID_ABOVE_THE_FJORD = "music_nf_001"
    const val TRACK_ID_DAWN_OVER_FROZEN_PINE = "music_nf_002"
    const val TRACK_ID_AIR_NOMADS = "music_ed_001"
    const val TRACK_ID_WATER_TRIBES = "music_ed_002"

    const val ASSET_ABOVE_THE_FJORD = "demo_music/nordic_fantasy/above_the_fjord.ogg"
    const val ASSET_DAWN_OVER_FROZEN_PINE = "demo_music/nordic_fantasy/dawn_over_frozen_pine.ogg"
    const val ASSET_AIR_NOMADS = "demo_music/elemental_dreams/air_nomads.ogg"
    const val ASSET_WATER_TRIBES = "demo_music/elemental_dreams/water_tribes.ogg"

    /**
     * Mapa explícito `trackId -> caminho exato em assets/` para as 4 músicas de demonstração.
     */
    val OFFICIAL_DEMO_TRACK_ASSETS: Map<String, String> = mapOf(
        TRACK_ID_ABOVE_THE_FJORD to ASSET_ABOVE_THE_FJORD,
        TRACK_ID_DAWN_OVER_FROZEN_PINE to ASSET_DAWN_OVER_FROZEN_PINE,
        TRACK_ID_AIR_NOMADS to ASSET_AIR_NOMADS,
        TRACK_ID_WATER_TRIBES to ASSET_WATER_TRIBES
    )

    /**
     * Mapa explícito e determinístico `trackId -> caminho relativo da capa individual no R2`
     * para as 30 faixas do álbum Elemental Dreams confirmadas no bucket Cloudflare R2.
     *
     * Garante compatibilidade imediata mesmo quando o `catalog.json` remoto não declara
     * o campo `coverUrl` individual em cada faixa.
     */
    val OFFICIAL_TRACK_COVERS: Map<String, String> = mapOf(
        "music_ed_001" to "covers/elemental_dreams/air_nomads.webp",
        "music_ed_002" to "covers/elemental_dreams/water_tribes.webp",
        "music_ed_003" to "covers/elemental_dreams/earth_kingdom.webp",
        "music_ed_004" to "covers/elemental_dreams/fire_nation.webp",
        "music_ed_005" to "covers/elemental_dreams/spirit_world.webp",
        "music_ed_006" to "covers/elemental_dreams/walls_of_ba_sing_se.webp",
        "music_ed_007" to "covers/elemental_dreams/a_cup_of_peace.webp",
        "music_ed_008" to "covers/elemental_dreams/wa_shin_tongs_curiosity.webp",
        "music_ed_009" to "covers/elemental_dreams/gentle_heart_from_the_south.webp",
        "music_ed_010" to "covers/elemental_dreams/like_a_water_tribe_warrior.webp",
        "music_ed_011" to "covers/elemental_dreams/grounded_in_silence.webp",
        "music_ed_012" to "covers/elemental_dreams/good_friends_from_the_sky.webp",
        "music_ed_013" to "covers/elemental_dreams/the_exiled_prince.webp",
        "music_ed_014" to "covers/elemental_dreams/throne_of_eternal_flames.webp",
        "music_ed_015" to "covers/elemental_dreams/admiral_ashes_of_the_northern_moon.webp",
        "music_ed_016" to "covers/elemental_dreams/blue_flame_obsession.webp",
        "music_ed_017" to "covers/elemental_dreams/echoes_of_a_forgotten_era.webp",
        "music_ed_018" to "covers/elemental_dreams/a_kyoshi_legacy.webp",
        "music_ed_019" to "covers/elemental_dreams/its_the_moon_itself.webp",
        "music_ed_020" to "covers/elemental_dreams/the_avatar_state.webp",
        "music_ed_021" to "covers/elemental_dreams/secret_tunnel.webp",
        "music_ed_022" to "covers/elemental_dreams/beware_the_bloodbending.webp",
        "music_ed_023" to "covers/elemental_dreams/before_the_four_elements.webp",
        "music_ed_024" to "covers/elemental_dreams/barefoot_on_the_metal.webp",
        "music_ed_025" to "covers/elemental_dreams/my_cabbages.webp",
        "music_ed_026" to "covers/elemental_dreams/cactus_juice.webp",
        "music_ed_027" to "covers/elemental_dreams/painted_in_green.webp",
        "music_ed_028" to "covers/elemental_dreams/blue_spirit.webp",
        "music_ed_029" to "covers/elemental_dreams/hidden_in_plain_sight.webp",
        "music_ed_030" to "covers/elemental_dreams/until_we_meet_again.webp"
    )

    /**
     * Retorna o caminho exato do asset local de demonstração para o [trackId] informado,
     * ou `null` caso a música não faça parte das 4 faixas embutidas no APK.
     */
    fun getExactAssetPathForTrackId(trackId: String): String? =
        OFFICIAL_DEMO_TRACK_ASSETS[trackId]

    /**
     * Resolve de forma determinística a URL/caminho da capa individual de uma faixa:
     * 1. Se o `catalog.json` trouxer `coverUrl` explícito na faixa, utiliza-o diretamente.
     * 2. Se o `trackId` estiver no registro explícito [OFFICIAL_TRACK_COVERS], utiliza o caminho verificado.
     * 3. Caso contrário, deriva o caminho determinístico `covers/<albumId>/<nome_base_normalizado>.webp`
     *    a partir do nome-base do arquivo de áudio.
     */
    fun resolveTrackCoverUrl(
        albumId: String,
        trackId: String,
        remoteAudioPath: String,
        explicitTrackCoverUrl: String?
    ): String? {
        explicitTrackCoverUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        OFFICIAL_TRACK_COVERS[trackId]?.let { return it }
        return deriveCoverPathFromAudioPath(albumId = albumId, remoteAudioPath = remoteAudioPath)
    }

    /**
     * Constrói o caminho relativo da capa individual seguindo o padrão do R2:
     * `albums/<albumId>/<nome_base>.ogg` -> `covers/<albumId>/<nome_base_normalizado>.webp`.
     */
    fun deriveCoverPathFromAudioPath(albumId: String, remoteAudioPath: String): String? {
        val cleanAlbumId = albumId.trim().trim('/')
        val fileName = remoteAudioPath.trim().substringAfterLast('/').trim()
        if (cleanAlbumId.isEmpty() || fileName.isEmpty()) return null

        val baseName = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
            .trim()
            .lowercase(Locale.ROOT)
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("\\s+"), "_")

        if (baseName.isEmpty()) return null
        return "covers/$cleanAlbumId/$baseName.webp"
    }
}

/**
 * Contrato único do manifesto estático `catalog.json` hospedado no Cloudflare R2:
 *
 * ```json
 * {
 *   "version": 1,
 *   "albums": [
 *     {
 *       "id": "nordic_fantasy",
 *       "title": "Nordic Fantasy",
 *       "artist": "mTaaz",
 *       "description": "Montanhas, aurora boreal e florestas geladas.",
 *       "coverUrl": "covers/nordic_fantasy.jpg",
 *       "tracks": [
 *         {
 *           "id": "music_nf_001",
 *           "title": "Above the Fjord",
 *           "artist": "mTaaz",
 *           "description": null,
 *           "remoteAudioPath": "albums/nordic_fantasy/above_the_fjord.ogg",
 *           "durationMs": 174263,
 *           "fileSizeBytes": 2175397,
 *           "trackOrder": 1,
 *           "coverUrl": "covers/nordic_fantasy/above_the_fjord.webp"
 *         }
 *       ]
 *     }
 *   ]
 * }
 * ```
 */
@Serializable
data class RemoteMusicCatalogDto(
    val version: Int = 1,
    val albums: List<RemoteMusicAlbumDto> = emptyList()
)

/**
 * Representa um álbum musical dentro do `catalog.json`, contendo sua lista aninhada de [tracks].
 */
@Serializable
data class RemoteMusicAlbumDto(
    val id: String,
    val title: String,
    val artist: String = MusicAlbum.DEFAULT_ARTIST,
    val description: String = "",
    val descriptions: Map<String, String> = emptyMap(),
    val coverUrl: String? = null,
    val paletteKey: String? = null,
    val tracks: List<RemoteMusicTrackDto> = emptyList()
)

/**
 * Representa uma música aninhada dentro de um [RemoteMusicAlbumDto] no `catalog.json`.
 * O campo [remoteAudioPath] aponta para o arquivo no Cloudflare R2 e é independente do caminho
 * local embutido no APK (`assets/demo_music/`).
 * O campo [coverUrl] aponta para a capa individual da faixa no Cloudflare R2.
 */
@Serializable
data class RemoteMusicTrackDto(
    val id: String,
    val title: String,
    val artist: String? = null,
    val description: String? = null,
    val remoteAudioPath: String,
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val trackOrder: Int? = null,
    val coverUrl: String? = null,
    val defaultVolume: Float? = null,
    val isLoopable: Boolean? = null
)

/**
 * Parser determinístico do `catalog.json` remoto, que atua como única fonte de verdade
 * para os álbuns e músicas exibidos no aplicativo.
 */
object RemoteCatalogParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
    }

    fun parseCatalogJson(rawJson: String): RemoteMusicCatalogDto? {
        if (rawJson.isBlank()) return null
        return try {
            jsonParser.decodeFromString(RemoteMusicCatalogDto.serializer(), rawJson)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeCatalogJson(dto: RemoteMusicCatalogDto): String {
        return jsonParser.encodeToString(RemoteMusicCatalogDto.serializer(), dto)
    }

    /**
     * Converte o [RemoteMusicCatalogDto] na lista de [MusicAlbum] do domínio, associando
     * explicitamente o caminho de asset local (`assets/demo_music/...`) apenas para os 4 IDs
     * de demonstração registrados em [LocalDemoMusicAssets] e resolvendo de forma determinística
     * a capa individual de cada faixa.
     */
    fun toDomainAlbums(dto: RemoteMusicCatalogDto?): List<MusicAlbum> {
        if (dto == null) return emptyList()

        return dto.albums
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .map { albumDto ->
                val cleanAlbumId = albumDto.id.trim()
                val cleanAlbumArtist = albumDto.artist.trim().ifEmpty { MusicAlbum.DEFAULT_ARTIST }
                val cleanAlbumCoverUrl = albumDto.coverUrl?.trim()?.takeIf { it.isNotEmpty() }

                val domainTracks = albumDto.tracks
                    .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.remoteAudioPath.isNotBlank() }
                    .mapIndexed { index, trackDto ->
                        val cleanTrackId = trackDto.id.trim()
                        val cleanRemoteAudioPath = trackDto.remoteAudioPath.trim()
                        val exactBundledAssetPath =
                            LocalDemoMusicAssets.getExactAssetPathForTrackId(cleanTrackId)
                        val resolvedTrackCoverUrl = LocalDemoMusicAssets.resolveTrackCoverUrl(
                            albumId = cleanAlbumId,
                            trackId = cleanTrackId,
                            remoteAudioPath = cleanRemoteAudioPath,
                            explicitTrackCoverUrl = trackDto.coverUrl
                        ) ?: cleanAlbumCoverUrl

                        MusicTrack(
                            id = cleanTrackId,
                            albumId = cleanAlbumId,
                            title = trackDto.title.trim(),
                            artist = trackDto.artist?.trim()?.takeIf { it.isNotEmpty() }
                                ?: cleanAlbumArtist,
                            description = trackDto.description,
                            remoteAudioPath = cleanRemoteAudioPath,
                            localAssetPath = exactBundledAssetPath,
                            fileSizeBytes = trackDto.fileSizeBytes?.takeIf { it > 0L },
                            coverUrl = resolvedTrackCoverUrl,
                            durationMs = trackDto.durationMs?.takeIf { it > 0L },
                            trackOrder = trackDto.trackOrder ?: (index + 1),
                            defaultVolume = trackDto.defaultVolume ?: MusicTrack.DEFAULT_MUSIC_VOLUME,
                            isLoopable = trackDto.isLoopable ?: true
                        )
                    }
                    .sortedBy { it.trackOrder }

                MusicAlbum(
                    id = cleanAlbumId,
                    title = albumDto.title.trim(),
                    artist = cleanAlbumArtist,
                    description = albumDto.description.trim(),
                    descriptions = albumDto.descriptions
                        .mapNotNull { (lang, desc) ->
                            val cleanLang = lang.trim()
                            val cleanDesc = desc.trim()
                            if (cleanLang.isNotEmpty() && cleanDesc.isNotEmpty()) cleanLang to cleanDesc else null
                        }
                        .toMap(),
                    coverUrl = cleanAlbumCoverUrl,
                    paletteKey = albumDto.paletteKey?.trim()?.takeIf { it.isNotEmpty() }
                        ?: cleanAlbumId,
                    tracks = domainTracks
                )
            }
    }
}
