package com.taaz.relaxaedorme.data.remote

import com.taaz.relaxaedorme.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Configuração pública centralizada para integração com o bucket estático do Cloudflare R2.
 *
 * Diretrizes de segurança:
 * - Na versão pública/comunitária, [BASE_PUBLIC_URL] é lida de [BuildConfig.R2_BASE_PUBLIC_URL]
 *   (vazia por padrão, podendo ser informada via `-PR2_BASE_PUBLIC_URL=...` ou variável de ambiente).
 * - Nenhuma credencial privada (`Account ID`, `Access Key ID`, `Secret Access Key` ou token de API)
 *   é armazenada no código-fonte ou no APK.
 */
object CloudflareR2Config {

    /**
     * Endereço público base configurável do bucket Cloudflare R2.
     */
    val BASE_PUBLIC_URL: String = BuildConfig.R2_BASE_PUBLIC_URL

    /**
     * Caminho relativo do manifesto estático de catálogo no bucket R2.
     */
    const val CATALOG_RELATIVE_PATH: String = "catalog.json"

    /**
     * Timeout de conexão HTTP em milissegundos.
     */
    const val CONNECT_TIMEOUT_MS: Int = 15_000

    /**
     * Timeout de leitura HTTP em milissegundos.
     */
    const val READ_TIMEOUT_MS: Int = 30_000

    /**
     * Indica se uma URL pública base HTTPS válida está configurada.
     */
    fun isRemoteConfigured(baseUrl: String = BASE_PUBLIC_URL): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.isNotEmpty() &&
            (trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true))
    }

    /**
     * Constrói a URL completa do `catalog.json` hospedado no R2, ou `null` caso [baseUrl]
     * esteja em branco.
     */
    fun buildCatalogUrl(
        baseUrl: String = BASE_PUBLIC_URL,
        catalogRelativePath: String = CATALOG_RELATIVE_PATH
    ): String? {
        return buildPublicUrl(
            relativePath = catalogRelativePath,
            baseUrl = baseUrl
        )
    }

    /**
     * Constrói a URL completa de um recurso hospedado no R2, codificando cada segmento
     * do caminho em UTF-8 compatível com RFC 3986 (espaços convertidos para `%20`).
     *
     * Caso [relativePath] já seja uma URL completa (`https://...`), retorna-a normalizada.
     * Caso [baseUrl] esteja em branco, retorna `null`.
     */
    fun buildPublicUrl(
        relativePath: String,
        baseUrl: String = BASE_PUBLIC_URL
    ): String? {
        val cleanPath = relativePath.trim()
        if (cleanPath.isEmpty()) return null

        if (
            cleanPath.startsWith("https://", ignoreCase = true) ||
            cleanPath.startsWith("http://", ignoreCase = true)
        ) {
            return encodeUrlPathSegmentsIfNeeded(cleanPath)
        }

        if (!isRemoteConfigured(baseUrl)) return null

        val normalizedBase = baseUrl.trim().trimEnd('/')
        val encodedRelativePath = encodeRelativePath(cleanPath)
        if (encodedRelativePath.isEmpty()) return null

        return "$normalizedBase/$encodedRelativePath"
    }

    /**
     * Codifica segmentos de um caminho relativo preservando `/` e convertendo espaços em `%20`.
     */
    fun encodeRelativePath(relativePath: String): String {
        return relativePath
            .trim()
            .trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("%7E", "~")
            }
    }

    private fun encodeUrlPathSegmentsIfNeeded(fullUrl: String): String {
        val schemeSeparator = fullUrl.indexOf("://")
        if (schemeSeparator < 0) return fullUrl
        val pathStart = fullUrl.indexOf('/', startIndex = schemeSeparator + 3)
        if (pathStart < 0) return fullUrl

        val origin = fullUrl.substring(0, pathStart)
        val rawPath = fullUrl.substring(pathStart + 1)
        if (rawPath.isEmpty()) return origin

        if (rawPath.contains('%')) {
            return "$origin/${rawPath.replace(" ", "%20")}"
        }
        return "$origin/${encodeRelativePath(rawPath)}"
    }
}
