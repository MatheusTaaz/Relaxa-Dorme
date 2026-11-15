package com.taaz.relaxaedorme.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.taaz.relaxaedorme.data.remote.CloudflareR2Config
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.CategoryPalette
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Componente independente responsável pelo carregamento e cache (memória + disco)
 * das imagens de capa individuais de músicas, capas de álbuns e ilustrações do aplicativo.
 *
 * Isolamento de falhas:
 * - Qualquer falha de rede, decodificação ou leitura de arquivo é tratada silenciosamente
 *   retornando `null`, sem jamais interferir no player, no mixer ou no serviço de reprodução.
 * - Mantém cache em memória ([memoryCache]), cache persistente em disco (`filesDir/artwork_cache/`)
 *   e registro de fontes indisponíveis na sessão ([failedSources]) para evitar downloads repetidos
 *   em recomposições de interface.
 */
object ArtworkCacheLoader {

    private const val ARTWORK_CACHE_DIR = "artwork_cache"
    private const val MEMORY_CACHE_MAX_KB = 24 * 1024 // 24 MB
    private val memoryCache = object : LruCache<String, ImageBitmap>(MEMORY_CACHE_MAX_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * 4L
            return (bytes / 1024L).toInt().coerceAtLeast(1)
        }
    }
    private val failedSources = ConcurrentHashMap.newKeySet<String>()

    fun clearFailedCache() {
        failedSources.clear()
    }

    private fun sizeBucket(targetMaxPx: Int): String {
        return when {
            targetMaxPx <= 0 -> "full"
            targetMaxPx <= 256 -> "thumb"
            targetMaxPx <= 512 -> "medium"
            else -> "full"
        }
    }

    private fun memoryKey(source: String, targetMaxPx: Int): String {
        return "${sizeBucket(targetMaxPx)}|$source"
    }

    fun getFromMemory(source: String?, targetMaxPx: Int = 0): ImageBitmap? {
        val clean = source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        memoryCache.get(memoryKey(clean, targetMaxPx))?.let { return it }
        extractBundledAssetCandidate(clean)?.let { candidate ->
            memoryCache.get(memoryKey(candidate, targetMaxPx))?.let { return it }
        }
        return null
    }

    fun getFromMemoryWithFallback(
        primarySource: String?,
        fallbackSource: String? = null,
        targetMaxPx: Int = 0
    ): ImageBitmap? {
        getFromMemory(primarySource, targetMaxPx)?.let { return it }
        val primaryClean = primarySource?.trim().orEmpty()
        if (primaryClean.isEmpty() || primaryClean in failedSources) {
            getFromMemory(fallbackSource, targetMaxPx)?.let { return it }
        }
        return null
    }

    private fun loadAssetInternal(
        context: Context,
        assetPath: String?,
        targetMaxPx: Int = 0
    ): ImageBitmap? {
        val cleanPath = assetPath?.trim()
            ?.removePrefix("file:///android_asset/")
            ?.trimStart('/')
            ?: return null
        if (cleanPath.isEmpty()) return null

        val key = memoryKey(cleanPath, targetMaxPx)
        memoryCache.get(key)?.let { return it }

        val decoded = runCatching {
            val sampleSize = if (targetMaxPx > 0) {
                context.assets.open(cleanPath).use { boundsStream ->
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(boundsStream, null, boundsOpts)
                    calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetMaxPx)
                }
            } else {
                1
            }

            context.assets.open(cleanPath).use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeStream(inputStream, null, options)?.asImageBitmap()
            }
        }.getOrNull()

        if (decoded != null) {
            memoryCache.put(key, decoded)
        }
        return decoded
    }

    /**
     * Carrega a capa principal ([primarySource]) e, caso ela não exista ou falhe ao carregar,
     * faz fallback determinístico para [fallbackSource] (capa geral do álbum).
     */
    suspend fun loadCoverWithFallback(
        context: Context,
        primarySource: String?,
        fallbackSource: String? = null,
        targetMaxPx: Int = 0
    ): ImageBitmap? {
        val primaryClean = primarySource?.trim()?.takeIf { it.isNotEmpty() }
        if (primaryClean != null) {
            val primaryBitmap = loadCoverBitmap(context, primaryClean, targetMaxPx)
            if (primaryBitmap != null) {
                return primaryBitmap
            }
        }

        val fallbackClean = fallbackSource?.trim()?.takeIf { it.isNotEmpty() && it != primaryClean }
        if (fallbackClean != null) {
            return loadCoverBitmap(context, fallbackClean, targetMaxPx)
        }

        return null
    }

    suspend fun loadCoverBitmap(
        context: Context,
        coverSource: String?,
        targetMaxPx: Int = 0
    ): ImageBitmap? {
        val raw = coverSource?.trim() ?: return null
        if (raw.isEmpty()) return null

        val rawKey = memoryKey(raw, targetMaxPx)
        memoryCache.get(rawKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            memoryCache.get(rawKey)?.let { return@withContext it }

            // 1. Verifica se existe como asset local direto (ou sufixo covers/... embarcado no APK)
            val localCandidate = if (
                !raw.startsWith("http://", ignoreCase = true) &&
                !raw.startsWith("https://", ignoreCase = true)
            ) {
                raw
            } else {
                extractBundledAssetCandidate(raw)
            }
            if (localCandidate != null) {
                loadAssetInternal(context, localCandidate, targetMaxPx)?.let { assetBitmap ->
                    memoryCache.put(rawKey, assetBitmap)
                    failedSources.remove(raw)
                    return@withContext assetBitmap
                }
            }

            // 2. Verifica cache persistente em disco no armazenamento privado do app
            val remoteUrl = CloudflareR2Config.buildPublicUrl(raw)
            val cacheKey = remoteUrl ?: raw
            val cacheMemoryKey = memoryKey(cacheKey, targetMaxPx)
            val cacheDir = File(context.applicationContext.filesDir, ARTWORK_CACHE_DIR).apply { mkdirs() }
            val cacheFile = File(cacheDir, sha256Hex(cacheKey) + ".img")
            if (cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L) {
                val cachedBitmap = decodeFileSafely(cacheFile, targetMaxPx)
                if (cachedBitmap != null) {
                    memoryCache.put(rawKey, cachedBitmap)
                    memoryCache.put(cacheMemoryKey, cachedBitmap)
                    failedSources.remove(raw)
                    return@withContext cachedBitmap
                } else {
                    runCatching { cacheFile.delete() }
                }
            }

            if (raw in failedSources) {
                return@withContext null
            }

            // 3. Baixa a capa remotamente do Cloudflare R2 (ou URL HTTPS) e salva em cache local
            if (remoteUrl == null) {
                failedSources.add(raw)
                return@withContext null
            }

            val downloadedBytes = downloadImageBytesSafely(remoteUrl)
            if (downloadedBytes == null) {
                failedSources.add(raw)
                return@withContext null
            }

            val remoteBitmap = decodeBytesSafely(downloadedBytes, targetMaxPx)

            if (remoteBitmap != null) {
                runCatching {
                    cacheFile.writeBytes(downloadedBytes)
                }
                memoryCache.put(rawKey, remoteBitmap)
                memoryCache.put(cacheMemoryKey, remoteBitmap)
                failedSources.remove(raw)
            } else {
                failedSources.add(raw)
            }

            remoteBitmap
        }
    }

    private fun extractBundledAssetCandidate(source: String): String? {
        val clean = source.trim()
        val coversIndex = clean.indexOf("covers/", ignoreCase = true)
        if (coversIndex >= 0) {
            return clean.substring(coversIndex)
        }
        val ghostyIndex = clean.indexOf("ghosty/", ignoreCase = true)
        if (ghostyIndex >= 0) {
            return clean.substring(ghostyIndex)
        }
        return null
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqMaxPx: Int): Int {
        if (reqMaxPx <= 0 || rawWidth <= 0 || rawHeight <= 0) return 1
        var inSampleSize = 1
        while ((rawWidth / (inSampleSize * 2)) >= reqMaxPx ||
            (rawHeight / (inSampleSize * 2)) >= reqMaxPx
        ) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun decodeFileSafely(file: File, targetMaxPx: Int = 0): ImageBitmap? {
        return runCatching {
            val sampleSize = if (targetMaxPx > 0) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetMaxPx)
            } else {
                1
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        }.getOrNull()
    }

    private fun decodeBytesSafely(bytes: ByteArray, targetMaxPx: Int = 0): ImageBitmap? {
        return runCatching {
            val sampleSize = if (targetMaxPx > 0) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetMaxPx)
            } else {
                1
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }.getOrNull()
    }

    private fun downloadImageBytesSafely(urlString: String): ByteArray? {
        val connection = runCatching {
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CloudflareR2Config.CONNECT_TIMEOUT_MS
                readTimeout = CloudflareR2Config.READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = true
            }
        }.getOrNull() ?: return null

        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Hex(input: String): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.getOrElse {
            input.hashCode().toUInt().toString(16)
        }
    }
}

/**
 * Carrega e mantém em cache a imagem de capa (asset local ou URL remota do R2 com cache em disco),
 * realizando fallback automático para [fallbackCoverSource] caso a capa principal não exista ou falhe.
 */
@Composable
fun rememberAssetCoverBitmap(
    coverSource: String?,
    fallbackCoverSource: String? = null,
    targetMaxPx: Int = 0
): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(coverSource, fallbackCoverSource, targetMaxPx) {
        mutableStateOf(
            ArtworkCacheLoader.getFromMemoryWithFallback(
                primarySource = coverSource,
                fallbackSource = fallbackCoverSource,
                targetMaxPx = targetMaxPx
            )
        )
    }

    LaunchedEffect(coverSource, fallbackCoverSource, targetMaxPx) {
        if (!coverSource.isNullOrBlank() || !fallbackCoverSource.isNullOrBlank()) {
            val loaded = ArtworkCacheLoader.loadCoverWithFallback(
                context = context,
                primarySource = coverSource,
                fallbackSource = fallbackCoverSource,
                targetMaxPx = targetMaxPx
            )
            if (loaded != null || bitmap == null) {
                bitmap = loaded
            }
        } else {
            bitmap = null
        }
    }

    return bitmap
}

/**
 * Superfície visual para a área de capa de álbuns e músicas.
 *
 * - Tenta exibir [coverAssetPath] (capa individual da música ou capa do álbum).
 * - Caso [coverAssetPath] não exista ou falhe ao carregar, utiliza automaticamente [fallbackCoverAssetPath] (capa do álbum).
 * - Caso nenhuma imagem esteja disponível, apresenta o placeholder visual derivado de [palette].
 * - Quando [preserveOriginalAspectRatio] é `true`, exibe a imagem completa centralizada ([ContentScale.Fit])
 *   sobre um container preenchido pelo gradiente da paleta do álbum, evitando cortes de títulos ou personagens.
 */
@Composable
fun TrackArtworkSurface(
    palette: CategoryPalette,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = AppIcons.Music,
    coverAssetPath: String? = null,
    fallbackCoverAssetPath: String? = null,
    isPlaying: Boolean = false,
    iconSize: Dp = 24.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    preserveOriginalAspectRatio: Boolean = true,
    targetSizeDp: Dp? = null
) {
    val density = LocalDensity.current
    val targetMaxPx = remember(targetSizeDp, density.density) {
        targetSizeDp?.let { with(density) { it.toPx().roundToInt().coerceAtLeast(1) } } ?: 0
    }
    val coverBitmap = rememberAssetCoverBitmap(
        coverSource = coverAssetPath,
        fallbackCoverSource = fallbackCoverAssetPath,
        targetMaxPx = targetMaxPx
    )

    val backgroundBrush = remember(palette) {
        Brush.linearGradient(
            colors = listOf(
                palette.gradientStart,
                palette.gradientEnd
            )
        )
    }

    val borderColor = if (isPlaying) {
        palette.primaryAccent.copy(alpha = 0.42f)
    } else {
        AppColors.NightCardBorder.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = backgroundBrush)
            .border(width = 1.dp, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                contentScale = if (preserveOriginalAspectRatio) {
                    ContentScale.Fit
                } else {
                    ContentScale.Crop
                },
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize * 1.75f)
                        .clip(CircleShape)
                        .background(palette.primaryAccent.copy(alpha = if (isPlaying) 0.22f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        iconRes = iconRes,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = palette.primaryAccent
                    )
                }
            }
        }
    }
}
