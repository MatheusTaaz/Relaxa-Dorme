package com.taaz.relaxaedorme.ui.screens.home

import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.SleepTimerState
import com.taaz.relaxaedorme.audio.formatListeningDuration
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.repository.ListeningStatsSnapshot
import com.taaz.relaxaedorme.ui.components.GhostyAssets
import com.taaz.relaxaedorme.ui.components.GhostyBuddy
import com.taaz.relaxaedorme.ui.components.GhostyBuddyStatus
import com.taaz.relaxaedorme.ui.components.rememberAssetCoverBitmap
import com.taaz.relaxaedorme.ui.components.resolveGhostyBuddyState
import com.taaz.relaxaedorme.ui.i18n.AppLanguage
import com.taaz.relaxaedorme.ui.i18n.createLocalizedContext
import com.taaz.relaxaedorme.ui.i18n.formatLocalizedListeningStats
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.viewmodel.MainUiState

/**
 * Tela Inicial (Home — Etapa 9.1) com a Ghosty como protagonista central.
 *
 * Apresenta uma atmosfera noturna minimalista (#090C15) organizada em:
 * 1. Topo: Marca "relaxa e dorme" e acesso discreto ao modal Sobre.
 * 2. Centro: Ghosty em destaque sobre brilho radial difuso, com flutuação suave e reação ao toque.
 * 3. Mensagem tranquila: "Boa noite. / Hora de desacelerar."
 * 4. Ação principal: "Continuar ouvindo" sincronizada com o estado real de áudio.
 * 5. Atalhos diretos: "Álbuns" e "Sons".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    playbackState: MusicPlaybackState,
    mixerState: AmbientMixerState = AmbientMixerState(),
    sleepTimerState: SleepTimerState = SleepTimerState(),
    sleepTimerStateFlow: StateFlow<SleepTimerState>? = null,
    listeningStatsFlow: StateFlow<ListeningStatsSnapshot>? = null,
    currentLanguage: AppLanguage = AppLanguage.DEFAULT,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    hasSleepSessionEnded: Boolean = false,
    pendingWakeUpFromSleep: Boolean = false,
    onAcknowledgeWakeUp: () -> Unit = {},
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onToggleMusicPlayPause: () -> Unit = {},
    onResumeMixer: () -> Unit = {},
    onOpenExpandedPlayer: () -> Unit = {},
    onNavigateToMixer: () -> Unit = {},
    onNavigateToMusic: () -> Unit = {},
    onNavigateToSounds: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    isAnimationsActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isReducedMotionEnabled = remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        scale <= 0.05f
    }

    var isAboutSheetOpen by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val greetingSeed = rememberSaveable { Random.nextInt(0, 10_000) }
    var currentHourOfDay by remember {
        mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                currentHourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        while (true) {
            delay(60_000L)
            currentHourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }

    val homeGreeting = remember(currentHourOfDay, greetingSeed) {
        resolveHomeGreeting(hourOfDay = currentHourOfDay, seed = greetingSeed)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.NightBackground)
    ) {
        val isCompactHeight = maxHeight < 640.dp
        val heroStageSize = when {
            maxHeight < 600.dp -> 195.dp
            maxHeight < 720.dp -> 235.dp
            else -> 268.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = if (isCompactHeight) 10.dp else 16.dp,
                    bottom = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Topo: Marca limpa + Seletor discreto de idioma + Favoritos e Sobre
            GhostyBrandHeader(
                currentLanguage = currentLanguage,
                onSelectLanguage = onSelectLanguage,
                onOpenFavorites = onNavigateToFavorites,
                onOpenAbout = { isAboutSheetOpen = true }
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 16.dp))

            // 2. Área Central: Ghosty Buddy + Mensagem + Ação Principal + Atalhos
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeGhostyBuddySection(
                    playbackState = playbackState,
                    mixerState = mixerState,
                    fallbackSleepTimerState = sleepTimerState,
                    sleepTimerStateFlow = sleepTimerStateFlow,
                    hasSleepSessionEnded = hasSleepSessionEnded,
                    pendingWakeUpFromSleep = pendingWakeUpFromSleep,
                    stageSize = heroStageSize,
                    isAnimationRunning = isAnimationsActive && !isReducedMotionEnabled,
                    onAcknowledgeWakeUp = onAcknowledgeWakeUp
                )

                Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))

                val localizedGreetingTitle = when (homeGreeting.period) {
                    HomeTimeOfDayPeriod.MORNING -> stringResource(R.string.greeting_morning_title)
                    HomeTimeOfDayPeriod.AFTERNOON -> stringResource(R.string.greeting_afternoon_title)
                    HomeTimeOfDayPeriod.NIGHT -> stringResource(R.string.greeting_night_title)
                }
                val localizedGreetingSubtitle = when (homeGreeting.period) {
                    HomeTimeOfDayPeriod.MORNING -> when (abs(greetingSeed) % 4) {
                        0 -> stringResource(R.string.greeting_morning_sub_1)
                        1 -> stringResource(R.string.greeting_morning_sub_2)
                        2 -> stringResource(R.string.greeting_morning_sub_3)
                        else -> stringResource(R.string.greeting_morning_sub_4)
                    }
                    HomeTimeOfDayPeriod.AFTERNOON -> when (abs(greetingSeed) % 4) {
                        0 -> stringResource(R.string.greeting_afternoon_sub_1)
                        1 -> stringResource(R.string.greeting_afternoon_sub_2)
                        2 -> stringResource(R.string.greeting_afternoon_sub_3)
                        else -> stringResource(R.string.greeting_afternoon_sub_4)
                    }
                    HomeTimeOfDayPeriod.NIGHT -> when (abs(greetingSeed) % 4) {
                        0 -> stringResource(R.string.greeting_night_sub_1)
                        1 -> stringResource(R.string.greeting_night_sub_2)
                        2 -> stringResource(R.string.greeting_night_sub_3)
                        else -> stringResource(R.string.greeting_night_sub_4)
                    }
                }

                Text(
                    text = localizedGreetingTitle,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = if (isCompactHeight) 28.sp else 32.sp
                    ),
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = localizedGreetingSubtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 28.dp))

                ContinueListeningButton(
                    uiState = uiState,
                    playbackState = playbackState,
                    mixerState = mixerState,
                    onPlayTrack = onPlayTrack,
                    onToggleMusicPlayPause = onToggleMusicPlayPause,
                    onResumeMixer = onResumeMixer,
                    onOpenExpandedPlayer = onOpenExpandedPlayer,
                    onNavigateToMixer = onNavigateToMixer
                )

                Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 28.dp))

                HomeDestinationList(
                    albumsCount = uiState.musicAlbums.size,
                    activeMixerCount = mixerState.activeCount,
                    maxMixerLayers = mixerState.maxLayers,
                    onNavigateToMusic = onNavigateToMusic,
                    onNavigateToSounds = onNavigateToSounds,
                    onNavigateToMixer = onNavigateToMixer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (isAboutSheetOpen) {
        AboutBottomSheet(
            albumCount = uiState.musicAlbums.size,
            trackCount = uiState.allMusicTracks.size,
            currentLanguage = currentLanguage,
            listeningStatsFlow = listeningStatsFlow,
            onDismiss = { isAboutSheetOpen = false }
        )
    }
}

@Composable
private fun GhostyBrandHeader(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    letterSpacing = 0.sp
                ),
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenFavorites,
                    modifier = Modifier.size(48.dp)
                ) {
                    AppIcon(
                        iconRes = AppIcons.FavoriteBorder,
                        contentDescription = stringResource(R.string.home_favorites_cd),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.size(48.dp)
                ) {
                    AppIcon(
                        iconRes = AppIcons.Info,
                        contentDescription = stringResource(R.string.home_about_cd),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        HomeLanguageSelector(
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage,
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}

@Composable
private fun HomeLanguageSelector(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == currentLanguage
            val languageName = stringResource(language.accessibilityNameRes)
            val cd = if (isSelected) {
                stringResource(R.string.language_selected_cd, languageName)
            } else {
                stringResource(R.string.language_selector_cd, languageName)
            }
            Surface(
                onClick = { onSelectLanguage(language) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    AppColors.NightSurfaceVariant
                } else {
                    AppColors.NightBackground.copy(alpha = 0.4f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        AppColors.GhostyLavenderSoft.copy(alpha = 0.45f)
                    } else {
                        AppColors.NightCardBorder.copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier.semantics {
                    selected = isSelected
                    contentDescription = cd
                }
            ) {
                Text(
                    text = "${language.flagEmoji} ${language.shortCode}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) AppColors.TextPrimary else AppColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeDestinationList(
    albumsCount: Int,
    activeMixerCount: Int,
    maxMixerLayers: Int,
    onNavigateToMusic: () -> Unit,
    onNavigateToSounds: () -> Unit,
    onNavigateToMixer: () -> Unit
) {
    val musicSubtitle = if (albumsCount > 0) {
        pluralStringResource(R.plurals.home_albums_available, albumsCount, albumsCount)
    } else {
        stringResource(R.string.home_albums_and_downloads)
    }

    val mixerSubtitle = if (activeMixerCount > 0) {
        pluralStringResource(R.plurals.home_mixer_active_sounds, activeMixerCount, activeMixerCount)
    } else {
        stringResource(R.string.home_mixer_combine_up_to, maxMixerLayers)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.35f))

        HomeDestinationRow(
            title = stringResource(R.string.home_dest_music),
            subtitle = musicSubtitle,
            iconRes = AppIcons.Music,
            onClick = onNavigateToMusic
        )

        HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.35f))

        HomeDestinationRow(
            title = stringResource(R.string.home_dest_sounds),
            subtitle = stringResource(R.string.home_sounds_subtitle),
            iconRes = AppIcons.Sounds,
            onClick = onNavigateToSounds
        )

        HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.35f))

        HomeDestinationRow(
            title = stringResource(R.string.home_dest_mixer),
            subtitle = mixerSubtitle,
            iconRes = AppIcons.Mixer,
            onClick = onNavigateToMixer
        )

        HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.35f))
    }
}

@Composable
private fun HomeDestinationRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            iconRes = iconRes,
            contentDescription = null,
            tint = AppColors.MoonlightPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }

        AppIcon(
            iconRes = AppIcons.ChevronRight,
            contentDescription = null,
            tint = AppColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

internal data class AboutAppSummary(
    val appName: String,
    val creator: String,
    val versionLabel: String,
    val albumsLabel: String,
    val tracksLabel: String
)

internal fun resolveAboutAppSummary(
    versionName: String,
    albumCount: Int,
    trackCount: Int
): AboutAppSummary {
    val safeVersion = versionName.ifBlank { "1.0" }
    val safeAlbums = albumCount.coerceAtLeast(0)
    val safeTracks = trackCount.coerceAtLeast(0)
    return AboutAppSummary(
        appName = "Relaxa e Dorme",
        creator = "mTaaz",
        versionLabel = "Versão $safeVersion",
        albumsLabel = if (safeAlbums == 1) "1 álbum" else "$safeAlbums álbuns",
        tracksLabel = if (safeTracks == 1) "1 faixa" else "$safeTracks faixas"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutBottomSheet(
    albumCount: Int,
    trackCount: Int,
    currentLanguage: AppLanguage,
    listeningStatsFlow: StateFlow<ListeningStatsSnapshot>?,
    onDismiss: () -> Unit
) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, currentLanguage) {
        createLocalizedContext(baseContext, currentLanguage)
    }
    val appVersion = remember(baseContext) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                baseContext.packageManager.getPackageInfo(
                    baseContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                baseContext.packageManager.getPackageInfo(baseContext.packageName, 0)
            }
            info.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ghostySignatureBitmap = rememberAssetCoverBitmap(GhostyAssets.SMILE)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.NightElevatedSurface,
        contentColor = AppColors.TextPrimary
    ) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration,
            LocalResources provides localizedContext.resources
        ) {
            val localizedAppName = stringResource(R.string.app_name)
            val localizedVersionLabel = stringResource(R.string.about_value_version, appVersion)
            val localizedAlbumsLabel = pluralStringResource(R.plurals.album_count, albumCount, albumCount)
            val localizedTracksLabel = pluralStringResource(R.plurals.track_count, trackCount, trackCount)

            Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Identidade visual com assinatura discreta da Ghosty
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (ghostySignatureBitmap != null) {
                    Image(
                        bitmap = ghostySignatureBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    AppIcon(
                        iconRes = AppIcons.Bedtime,
                        contentDescription = null,
                        tint = AppColors.GhostyMoonCream,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Column {
                    Text(
                        text = localizedAppName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.app_created_by),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Texto autoral
            Text(
                text = stringResource(R.string.about_editorial_body),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.38f))
            Spacer(modifier = Modifier.height(20.dp))

            // Seu tempo de tranquilidade (coleta isolada para não recompor o restante da tela)
            AboutListeningStatsSection(
                currentLanguage = currentLanguage,
                listeningStatsFlow = listeningStatsFlow
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.38f))
            Spacer(modifier = Modifier.height(20.dp))

            // Informações do aplicativo
            Text(
                text = stringResource(R.string.about_app_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            AboutInfoRow(label = stringResource(R.string.about_label_name), value = localizedAppName)
            Spacer(modifier = Modifier.height(6.dp))
            AboutInfoRow(
                label = stringResource(R.string.about_label_creator),
                value = stringResource(R.string.app_creator)
            )
            Spacer(modifier = Modifier.height(6.dp))
            AboutInfoRow(label = stringResource(R.string.about_label_version), value = localizedVersionLabel)
            Spacer(modifier = Modifier.height(6.dp))
            AboutInfoRow(
                label = stringResource(R.string.about_label_catalog),
                value = "$localizedAlbumsLabel • $localizedTracksLabel"
            )

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = AppColors.NightCardBorder.copy(alpha = 0.38f))
            Spacer(modifier = Modifier.height(18.dp))

            // Privacidade e transparência
            Text(
                text = stringResource(R.string.about_privacy_title),
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_privacy_body),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = AppColors.TextMuted
            )
            }
        }
    }
}

@Composable
private fun AboutListeningStatsSection(
    currentLanguage: AppLanguage,
    listeningStatsFlow: StateFlow<ListeningStatsSnapshot>?
) {
    val context = LocalContext.current
    val stats = if (listeningStatsFlow != null) {
        listeningStatsFlow.collectAsStateWithLifecycle().value
    } else {
        ListeningStatsSnapshot()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.about_stats_title),
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.about_stats_total),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatLocalizedListeningStats(context, stats.totalMs, currentLanguage.locale),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_stats_music),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatLocalizedListeningStats(context, stats.musicMs, currentLanguage.locale),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextSecondary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_stats_ambient),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatLocalizedListeningStats(context, stats.ambientMs, currentLanguage.locale),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.about_stats_local_note),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun ContinueListeningButton(
    uiState: MainUiState,
    playbackState: MusicPlaybackState,
    mixerState: AmbientMixerState,
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onToggleMusicPlayPause: () -> Unit,
    onResumeMixer: () -> Unit,
    onOpenExpandedPlayer: () -> Unit,
    onNavigateToMixer: () -> Unit
) {
    val activeTrack = playbackState.currentTrack
    val recentTrack = uiState.recentTracks.firstOrNull()
    val fallbackTrack = remember(uiState.allMusicTracks) {
        uiState.allMusicTracks.firstOrNull { it.albumId == MusicAlbum.ID_NORDIC_FANTASY }
            ?: uiState.allMusicTracks.firstOrNull()
    }

    val isCurrentlyPlaying = playbackState.isPlaying || mixerState.anyPlaying

    val primaryLabel: String
    val secondaryLabel: String?
    val onActionClick: () -> Unit

    val continueLabel = stringResource(R.string.home_continue_listening)
    val mixerFallbackSubtitle = stringResource(R.string.home_continue_mixer_subtitle)
    val musicFallbackTitle = stringResource(R.string.home_dest_music)

    when {
        activeTrack != null -> {
            val albumTitle = playbackState.currentAlbum?.title
                ?: uiState.getAlbumForTrack(activeTrack)?.title
            primaryLabel = continueLabel
            secondaryLabel = if (!albumTitle.isNullOrBlank()) {
                "${activeTrack.title} • $albumTitle"
            } else {
                "${activeTrack.title} • ${activeTrack.artist}"
            }
            onActionClick = {
                if (playbackState.isPlaying) {
                    onOpenExpandedPlayer()
                } else {
                    onToggleMusicPlayPause()
                }
            }
        }

        mixerState.hasActiveSounds -> {
            val soundNames = mixerState.activeLayers.joinToString(" • ") { it.sound.name }
            primaryLabel = continueLabel
            secondaryLabel = soundNames.ifBlank { mixerFallbackSubtitle }
            onActionClick = {
                if (mixerState.anyPlaying) {
                    onNavigateToMixer()
                } else {
                    onResumeMixer()
                }
            }
        }

        recentTrack != null -> {
            val album = uiState.getAlbumForTrack(recentTrack)
            val albumQueue = if (album != null) {
                album.tracks.ifEmpty {
                    uiState.allMusicTracks.filter { it.albumId == album.id }.ifEmpty { uiState.recentTracks }
                }
            } else {
                uiState.recentTracks
            }
            primaryLabel = continueLabel
            secondaryLabel = if (album != null) {
                "${recentTrack.title} • ${album.title}"
            } else {
                "${recentTrack.title} • ${recentTrack.artist}"
            }
            onActionClick = {
                onPlayTrack(
                    recentTrack,
                    albumQueue,
                    album?.title ?: continueLabel
                )
            }
        }

        fallbackTrack != null -> {
            val album = uiState.getAlbumForTrack(fallbackTrack)
            val albumQueue = if (album != null) {
                album.tracks.ifEmpty {
                    uiState.allMusicTracks.filter { it.albumId == album.id }.ifEmpty { listOf(fallbackTrack) }
                }
            } else {
                listOf(fallbackTrack)
            }
            primaryLabel = continueLabel
            secondaryLabel = if (album != null) {
                "${fallbackTrack.title} • ${album.title}"
            } else {
                fallbackTrack.title
            }
            onActionClick = {
                onPlayTrack(
                    fallbackTrack,
                    albumQueue,
                    album?.title ?: musicFallbackTitle
                )
            }
        }

        else -> return
    }

    Surface(
        shape = CircleShape,
        color = AppColors.GhostyContinuePill,
        shadowElevation = 4.dp,
        modifier = Modifier
            .widthIn(min = 220.dp, max = 340.dp)
            .fillMaxWidth(0.88f)
            .clip(CircleShape)
            .clickable(onClick = onActionClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AppIcon(
                iconRes = if (isCurrentlyPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = primaryLabel,
                tint = AppColors.GhostyContinueOnPill,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.GhostyContinueOnPill,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!secondaryLabel.isNullOrBlank()) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp
                        ),
                        color = AppColors.GhostyContinueOnPill.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGhostyBuddySection(
    playbackState: MusicPlaybackState,
    mixerState: AmbientMixerState,
    fallbackSleepTimerState: SleepTimerState,
    sleepTimerStateFlow: StateFlow<SleepTimerState>?,
    hasSleepSessionEnded: Boolean,
    pendingWakeUpFromSleep: Boolean,
    stageSize: Dp,
    isAnimationRunning: Boolean,
    onAcknowledgeWakeUp: () -> Unit
) {
    val activeSleepTimerState = if (sleepTimerStateFlow != null) {
        sleepTimerStateFlow.collectAsStateWithLifecycle().value
    } else {
        fallbackSleepTimerState
    }

    val buddyState = remember(
        playbackState.isPlaying,
        playbackState.currentTrack,
        mixerState.anyPlaying,
        mixerState.hasActiveSounds,
        activeSleepTimerState.isActive,
        activeSleepTimerState.isFadingOut,
        activeSleepTimerState.remainingMillis,
        activeSleepTimerState.fadeMultiplier,
        hasSleepSessionEnded,
        pendingWakeUpFromSleep
    ) {
        resolveGhostyBuddyState(
            playbackState = playbackState,
            mixerState = mixerState,
            sleepTimerState = activeSleepTimerState,
            hasSleepSessionEnded = hasSleepSessionEnded,
            hasPendingWakeUp = pendingWakeUpFromSleep,
            isBrowsingLibrary = false
        )
    }

    GhostyBuddy(
        state = buddyState,
        stageSize = stageSize,
        isAnimationRunning = isAnimationRunning,
        onTap = {
            if (buddyState.status == GhostyBuddyStatus.WAKING_UP) {
                onAcknowledgeWakeUp()
            }
        }
    )
}

enum class HomeTimeOfDayPeriod {
    MORNING,
    AFTERNOON,
    NIGHT
}

data class HomeGreeting(
    val period: HomeTimeOfDayPeriod,
    val title: String,
    val subtitle: String
)

private val MORNING_SUBTITLES = listOf(
    "Que seu dia comece com leveza.",
    "Respire fundo e aproveite a manhã.",
    "Um passo tranquilo de cada vez.",
    "Desejamos um começo de dia sereno."
)

private val AFTERNOON_SUBTITLES = listOf(
    "Que tal uma pausa para respirar?",
    "Reserve alguns minutos para desacelerar.",
    "Um momento de calma no meio do dia.",
    "Mantenha o ritmo leve nesta tarde."
)

private val NIGHT_SUBTITLES = listOf(
    "Hora de desacelerar.",
    "Prepare-se para uma noite tranquila.",
    "Respire fundo e descanse com calma.",
    "Seu espaço calmo para dormir melhor."
)

fun resolveHomePeriod(hourOfDay: Int): HomeTimeOfDayPeriod {
    val normalizedHour = ((hourOfDay % 24) + 24) % 24
    return when (normalizedHour) {
        in 5..11 -> HomeTimeOfDayPeriod.MORNING
        in 12..17 -> HomeTimeOfDayPeriod.AFTERNOON
        else -> HomeTimeOfDayPeriod.NIGHT
    }
}

fun resolveHomeGreeting(hourOfDay: Int, seed: Int = 0): HomeGreeting {
    val period = resolveHomePeriod(hourOfDay)
    val safeSeed = abs(seed)
    return when (period) {
        HomeTimeOfDayPeriod.MORNING -> HomeGreeting(
            period = period,
            title = "Bom dia!",
            subtitle = MORNING_SUBTITLES[safeSeed % MORNING_SUBTITLES.size]
        )

        HomeTimeOfDayPeriod.AFTERNOON -> HomeGreeting(
            period = period,
            title = "Boa tarde!",
            subtitle = AFTERNOON_SUBTITLES[safeSeed % AFTERNOON_SUBTITLES.size]
        )

        HomeTimeOfDayPeriod.NIGHT -> HomeGreeting(
            period = period,
            title = "Boa noite!",
            subtitle = NIGHT_SUBTITLES[safeSeed % NIGHT_SUBTITLES.size]
        )
    }
}
