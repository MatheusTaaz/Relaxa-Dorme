package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.remote.RemoteCatalogParser
import com.taaz.relaxaedorme.ui.i18n.AppLanguage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class InternationalizationTest {

    @Test
    fun `default language is Brazilian Portuguese and unknown codes fall back to pt-BR`() {
        assertEquals(AppLanguage.PT_BR, AppLanguage.DEFAULT)
        assertEquals("pt-BR", AppLanguage.DEFAULT.code)
        assertEquals(AppLanguage.PT_BR, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.PT_BR, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.PT_BR, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.PT_BR, AppLanguage.fromCode("pt"))
        assertEquals(AppLanguage.PT_BR, AppLanguage.fromCode("pt_BR"))
    }

    @Test
    fun `manual selection supports EN and ES with round-trip code lookup`() {
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en-US"))
        assertEquals(AppLanguage.ES, AppLanguage.fromCode("es"))
        assertEquals(AppLanguage.ES, AppLanguage.fromCode("es-419"))
        assertEquals("en", AppLanguage.EN.code)
        assertEquals("es", AppLanguage.ES.code)
        assertEquals("PT", AppLanguage.PT_BR.shortCode)
        assertEquals("EN", AppLanguage.EN.shortCode)
        assertEquals("ES", AppLanguage.ES.shortCode)
    }

    @Test
    fun `album localizedDescription returns translated text and falls back to Portuguese`() {
        val album = MusicAlbum(
            id = "nordic_fantasy",
            title = "Nordic Fantasy",
            artist = "mTaaz",
            description = "Paisagens sonoras nórdicas em português.",
            descriptions = mapOf(
                "en" to "Nordic soundscapes in English.",
                "es" to "Paisajes sonoros nórdicos en español."
            ),
            tracks = emptyList()
        )

        assertEquals("Paisagens sonoras nórdicas em português.", album.localizedDescription("pt-BR"))
        assertEquals("Nordic soundscapes in English.", album.localizedDescription("en"))
        assertEquals("Paisajes sonoros nórdicos en español.", album.localizedDescription("es"))
        assertEquals("Paisagens sonoras nórdicas em português.", album.localizedDescription("fr"))

        val legacyAlbum = album.copy(descriptions = emptyMap())
        assertEquals("Paisagens sonoras nórdicas em português.", legacyAlbum.localizedDescription("en"))
        assertEquals("Paisagens sonoras nórdicas em português.", legacyAlbum.localizedDescription("es"))
    }

    @Test
    fun `catalog json preserves original titles, artists, tracks, paths and localized descriptions`() {
        val sampleCatalogJson = """
            {
              "version": 1,
              "albums": [
                {
                  "id": "nordic_fantasy",
                  "title": "Nordic Fantasy",
                  "artist": "mTaaz",
                  "description": "Paisagens sonoras nórdicas inspiradas em fiordes silenciosos.",
                  "descriptions": {
                    "en": "Nordic soundscapes inspired by silent fjords.",
                    "es": "Paisajes sonoros nórdicos inspirados en fiordos silenciosos."
                  },
                  "coverUrl": "covers/nordic_fantasy.png",
                  "tracks": [
                    {
                      "id": "music_nf_001",
                      "title": "Above the Fjord",
                      "artist": "mTaaz",
                      "description": "Sopro gelado sobre águas profundas.",
                      "remoteAudioPath": "albums/nordic_fantasy/above_the_fjord.ogg",
                      "coverUrl": "covers/nordic_fantasy/above_the_fjord.webp"
                    }
                  ]
                },
                {
                  "id": "elemental_dreams",
                  "title": "Elemental Dreams",
                  "artist": "mTaaz",
                  "description": "Jornada instrumental pelos elementos da natureza.",
                  "descriptions": {
                    "en": "Instrumental journey through the elements of nature.",
                    "es": "Viaje instrumental por los elementos de la naturaleza."
                  },
                  "coverUrl": "covers/elemental_dreams.png",
                  "tracks": [
                    {
                      "id": "music_ed_001",
                      "title": "Air Nomads",
                      "artist": "mTaaz",
                      "description": "Brisa leve nas montanhas.",
                      "remoteAudioPath": "albums/elemental_dreams/air_nomads.ogg",
                      "coverUrl": "covers/elemental_dreams/air_nomads.webp"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val dto = RemoteCatalogParser.parseCatalogJson(sampleCatalogJson)
        assertNotNull("Catalog JSON fixture must parse cleanly", dto)
        val albums = RemoteCatalogParser.toDomainAlbums(dto!!)
        assertEquals(2, albums.size)

        val nordic = albums.first { it.id == "nordic_fantasy" }
        assertEquals("Nordic Fantasy", nordic.title)
        assertEquals("mTaaz", nordic.artist)
        assertTrue(nordic.description.isNotBlank())
        assertTrue(nordic.localizedDescription("en").isNotBlank())
        assertTrue(nordic.localizedDescription("es").isNotBlank())
        assertFalse(nordic.localizedDescription("en") == nordic.description)
        assertFalse(nordic.localizedDescription("es") == nordic.description)

        val elemental = albums.first { it.id == "elemental_dreams" }
        assertEquals("Elemental Dreams", elemental.title)
        assertEquals("mTaaz", elemental.artist)
        assertTrue(elemental.description.isNotBlank())
        assertTrue(elemental.localizedDescription("en").isNotBlank())
        assertTrue(elemental.localizedDescription("es").isNotBlank())
        assertFalse(elemental.localizedDescription("en") == elemental.description)
        assertFalse(elemental.localizedDescription("es") == elemental.description)

        // Tracks, titles, artist, remote paths and covers remain untouched
        albums.forEach { album ->
            assertFalse(album.coverUrl.isNullOrBlank())
            album.tracks.forEach { track ->
                assertTrue(track.title.isNotBlank())
                assertEquals("mTaaz", track.artist)
                assertFalse(track.remoteAudioPath.isNullOrBlank())
            }
        }
    }

    @Test
    fun `all three locale string files define matching keys, localized app names, and mTaaz`() {
        val resDir = File("src/main/res").takeIf { it.exists() }
            ?: File("app/src/main/res").takeIf { it.exists() }
        assertNotNull("res directory must exist", resDir)

        val ptStrings = parseResourceKeys(File(resDir, "values/strings.xml"))
        val enStrings = parseResourceKeys(File(resDir, "values-en/strings.xml"))
        val esStrings = parseResourceKeys(File(resDir, "values-es/strings.xml"))

        assertEquals("Relaxa e Dorme", ptStrings.strings["app_name"])
        assertEquals("Relax & Sleep", enStrings.strings["app_name"])
        assertEquals("Relaja y Duerme", esStrings.strings["app_name"])

        assertEquals("mTaaz", ptStrings.strings["app_creator"])
        assertEquals("mTaaz", enStrings.strings["app_creator"])
        assertEquals("mTaaz", esStrings.strings["app_creator"])

        assertEquals(ptStrings.strings.keys, enStrings.strings.keys)
        assertEquals(ptStrings.strings.keys, esStrings.strings.keys)
        assertEquals(ptStrings.plurals, enStrings.plurals)
        assertEquals(ptStrings.plurals, esStrings.plurals)
        assertTrue(ptStrings.plurals.contains("duration_seconds"))
        assertTrue(ptStrings.plurals.contains("duration_minutes"))
        assertTrue(ptStrings.plurals.contains("duration_hours"))

        assertEquals("Seu tempo de tranquilidade", ptStrings.strings["about_stats_title"])
        assertEquals("Your Relaxation Time", enStrings.strings["about_stats_title"])
        assertEquals("Tu tiempo de tranquilidad", esStrings.strings["about_stats_title"])

        assertEquals("Tempo com músicas", ptStrings.strings["about_stats_music"])
        assertEquals("Music time", enStrings.strings["about_stats_music"])
        assertEquals("Tiempo con música", esStrings.strings["about_stats_music"])

        assertEquals("Tempo com sons ambientes", ptStrings.strings["about_stats_ambient"])
        assertEquals("Ambient sound time", enStrings.strings["about_stats_ambient"])
        assertEquals("Tiempo con sonidos ambientales", esStrings.strings["about_stats_ambient"])

        assertTrue(ptStrings.strings["about_editorial_body"]?.contains("Uma criação mTaaz.") == true)
        assertTrue(enStrings.strings["about_editorial_body"]?.contains("A creation by mTaaz.") == true)
        assertTrue(esStrings.strings["about_editorial_body"]?.contains("Una creación de mTaaz.") == true)
    }

    private data class ParsedResources(
        val strings: Map<String, String>,
        val plurals: Set<String>
    )

    private fun parseResourceKeys(file: File): ParsedResources {
        assertTrue("Missing resource file: ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        val stringMap = mutableMapOf<String, String>()
        val pluralSet = mutableSetOf<String>()

        val stringNodes = root.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            stringMap[el.getAttribute("name")] = el.textContent
        }

        val pluralNodes = root.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            pluralSet.add(el.getAttribute("name"))
        }

        return ParsedResources(strings = stringMap, plurals = pluralSet)
    }
}
