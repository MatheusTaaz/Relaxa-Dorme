package com.taaz.relaxaedorme.data.catalog

import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.SoundCategory

/**
 * Catálogo centralizado da biblioteca de Sons Ambientes.
 *
 * Contém as 5 categorias iniciais e os 18 arquivos `.ogg` reais empacotados em
 * `app/src/main/assets/sounds/`, com caminhos físicos preservados, durações reais
 * e suporte a reprodução em loop contínuo.
 */
object SoundCatalog {

    /**
     * Categorias oficiais da biblioteca de Sons Ambientes.
     */
    val categories: List<SoundCategory> = listOf(
        SoundCategory(
            id = SoundCategory.ID_RAIN,
            name = "Chuva e tempestade",
            description = "Sons de chuva suave, chuva intensa, tempestades e trovões distantes.",
            assetDirectory = "sounds/rain",
            imageAssetPath = null
        ),
        SoundCategory(
            id = SoundCategory.ID_WATER,
            name = "Água",
            description = "Ondas do mar, correnteza de rio tranquilo e queda de cachoeira.",
            assetDirectory = "sounds/water",
            imageAssetPath = null
        ),
        SoundCategory(
            id = SoundCategory.ID_FOREST,
            name = "Floresta e animais",
            description = "Canto de pássaros na mata, grilos noturnos e brisa entre as árvores.",
            assetDirectory = "sounds/forest",
            imageAssetPath = null
        ),
        SoundCategory(
            id = SoundCategory.ID_FIRE,
            name = "Fogo",
            description = "Estalar aconchegante de fogueira ao ar livre, lareira acesa e brasas.",
            assetDirectory = "sounds/fire",
            imageAssetPath = null
        ),
        SoundCategory(
            id = SoundCategory.ID_NOISE,
            name = "Noises",
            description = "Frequências contínuas de ruído branco, rosa e marrom para foco e sono profundo.",
            assetDirectory = "sounds/noise",
            imageAssetPath = null
        )
    )

    private val categoriesById: Map<String, SoundCategory> =
        categories.associateBy { it.id }

    /**
     * Lista dos 18 sons ambientes reais disponíveis offline em `app/src/main/assets/sounds/`.
     */
    val sounds: List<AmbientSound> = listOf(
        // --- Categoria: Chuva e tempestade (rain) - 6 arquivos ---
        AmbientSound(
            id = "sound_rain_001",
            name = "Chuva Suave",
            description = "Garoa leve e contínua para acalmar a mente e induzir o sono.",
            categoryId = SoundCategory.ID_RAIN,
            audioAssetPath = "sounds/rain/light_rain.ogg",
            durationMs = 15_000L,
            defaultVolume = 0.65f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_rain_002",
            name = "Chuva Intensa",
            description = "Chuva forte e constante criando uma cortina sonora envolvente.",
            categoryId = SoundCategory.ID_RAIN,
            audioAssetPath = "sounds/rain/heavy_rain.ogg",
            durationMs = 12_174L,
            defaultVolume = 0.60f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_rain_003",
            name = "Tempestade Suave",
            description = "Chuva moderada acompanhada por trovoadas suaves ao longe.",
            categoryId = SoundCategory.ID_RAIN,
            audioAssetPath = "sounds/rain/light_storm.ogg",
            durationMs = 52_750L,
            defaultVolume = 0.60f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_rain_004",
            name = "Tempestade Intensa",
            description = "Temporal denso com chuva forte e atmosfera noturna acolhedora.",
            categoryId = SoundCategory.ID_RAIN,
            audioAssetPath = "sounds/rain/heavy_storm.ogg",
            durationMs = 18_000L,
            defaultVolume = 0.55f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_rain_005",
            name = "Trovão Distante",
            description = "Ressonância grave e serena de trovões ecoando no horizonte.",
            categoryId = SoundCategory.ID_RAIN,
            audioAssetPath = "sounds/rain/distant_thunder.ogg",
            durationMs = 9_502L,
            defaultVolume = 0.50f,
            isLoopable = true,
            supportsIntervalFrequency = true
        ),
        AmbientSound(
            id = "sound_rain_006",
            name = "Trovão Forte",
            description = "Estrondo marcante de trovão para enriquecer cenários de tempestade.",
            categoryId = SoundCategory.ID_RAIN,
            audioAssetPath = "sounds/rain/heavy_thunder.ogg",
            durationMs = 4_708L,
            defaultVolume = 0.45f,
            isLoopable = true,
            supportsIntervalFrequency = true
        ),

        // --- Categoria: Água (water) - 3 arquivos ---
        AmbientSound(
            id = "sound_water_001",
            name = "Ondas do Mar",
            description = "Quebrar ritmado e tranquilo de ondas na costa à beira-mar.",
            categoryId = SoundCategory.ID_WATER,
            audioAssetPath = "sounds/water/ocean_waves.ogg",
            durationMs = 48_246L,
            defaultVolume = 0.60f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_water_002",
            name = "Correnteza do Rio",
            description = "Fluxo cristalino e contínuo de água correndo sobre pedras.",
            categoryId = SoundCategory.ID_WATER,
            audioAssetPath = "sounds/water/river.ogg",
            durationMs = 38_650L,
            defaultVolume = 0.60f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_water_003",
            name = "Cachoeira",
            description = "Queda d'água constante e refrescante em meio à natureza.",
            categoryId = SoundCategory.ID_WATER,
            audioAssetPath = "sounds/water/waterfall.ogg",
            durationMs = 20_000L,
            defaultVolume = 0.50f,
            isLoopable = true
        ),

        // --- Categoria: Floresta e animais (forest) - 3 arquivos ---
        AmbientSound(
            id = "sound_forest_001",
            name = "Pássaros na Floresta",
            description = "Canto suave de aves silvestres entre as copas das árvores.",
            categoryId = SoundCategory.ID_FOREST,
            audioAssetPath = "sounds/forest/forest_birds.ogg",
            durationMs = 137_167L,
            defaultVolume = 0.55f,
            isLoopable = true,
            supportsIntervalFrequency = true
        ),
        AmbientSound(
            id = "sound_forest_002",
            name = "Grilos Noturnos",
            description = "Serenata noturna de grilos em uma floresta calma sob o luar.",
            categoryId = SoundCategory.ID_FOREST,
            audioAssetPath = "sounds/forest/forest_crickets.ogg",
            durationMs = 175_958L,
            defaultVolume = 0.50f,
            isLoopable = true,
            supportsIntervalFrequency = true
        ),
        AmbientSound(
            id = "sound_forest_003",
            name = "Vento na Floresta",
            description = "Brisa suave soprando entre folhas e galhos da mata.",
            categoryId = SoundCategory.ID_FOREST,
            audioAssetPath = "sounds/forest/wind_forest.ogg",
            durationMs = 125_354L,
            defaultVolume = 0.55f,
            isLoopable = true,
            supportsIntervalFrequency = true
        ),

        // --- Categoria: Fogo (fire) - 3 arquivos ---
        AmbientSound(
            id = "sound_fire_001",
            name = "Fogueira de Acampamento",
            description = "Estalar aconchegante de lenha queimando ao ar livre.",
            categoryId = SoundCategory.ID_FIRE,
            audioAssetPath = "sounds/fire/campfire.ogg",
            durationMs = 12_726L,
            defaultVolume = 0.60f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_fire_002",
            name = "Lareira Acesa",
            description = "Calor contínuo e crepitar suave de uma lareira interna.",
            categoryId = SoundCategory.ID_FIRE,
            audioAssetPath = "sounds/fire/fireplace.ogg",
            durationMs = 94_492L,
            defaultVolume = 0.65f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_fire_003",
            name = "Brasas Crepitantes",
            description = "Detalhe próximo de chamas e brasas estalando suavemente.",
            categoryId = SoundCategory.ID_FIRE,
            audioAssetPath = "sounds/fire/closeup_firebush.ogg",
            durationMs = 11_000L,
            defaultVolume = 0.50f,
            isLoopable = true,
            supportsIntervalFrequency = true
        ),

        // --- Categoria: Ruído ambiente (noise) - 3 arquivos ---
        AmbientSound(
            id = "sound_noise_001",
            name = "Ruído Branco",
            description = "Frequência uniforme ideal para mascarar ruídos externos e focar.",
            categoryId = SoundCategory.ID_NOISE,
            audioAssetPath = "sounds/noise/white_noise.ogg",
            durationMs = 64_000L,
            defaultVolume = 0.40f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_noise_002",
            name = "Ruído Rosa",
            description = "Textura sonora equilibrada e suave para relaxamento e sono contínuo.",
            categoryId = SoundCategory.ID_NOISE,
            audioAssetPath = "sounds/noise/pink_noise.ogg",
            durationMs = 10_000L,
            defaultVolume = 0.45f,
            isLoopable = true
        ),
        AmbientSound(
            id = "sound_noise_003",
            name = "Ruído Marrom",
            description = "Frequência grave, profunda e acolhedora para descanso mental.",
            categoryId = SoundCategory.ID_NOISE,
            audioAssetPath = "sounds/noise/brown_noise.ogg",
            durationMs = 10_000L,
            defaultVolume = 0.50f,
            isLoopable = true
        )
    )

    private val soundsById: Map<String, AmbientSound> =
        sounds.associateBy { it.id }

    /**
     * Busca uma categoria de som ambiente pelo seu [categoryId] estável.
     */
    fun getCategoryById(categoryId: String): SoundCategory? =
        categoriesById[categoryId]

    /**
     * Retorna todos os sons ambientes pertencentes a uma categoria específica.
     */
    fun getSoundsByCategory(categoryId: String): List<AmbientSound> =
        sounds.filter { it.categoryId == categoryId }

    /**
     * Busca um som ambiente pelo seu [soundId] único e estável.
     */
    fun getSoundById(soundId: String): AmbientSound? =
        soundsById[soundId]
}
