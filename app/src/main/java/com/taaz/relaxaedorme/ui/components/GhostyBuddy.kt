package com.taaz.relaxaedorme.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.SleepTimerState
import com.taaz.relaxaedorme.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * Caminhos oficiais das 16 ilustrações originais da personagem Ghosty em `app/src/main/assets/ghosty/`.
 */
object GhostyAssets {
    const val ORIGINAL = "ghosty/Ghosty og.png"
    const val BLANKET = "ghosty/Ghosty_blanket.png"
    const val HEADPHONE = "ghosty/Ghosty_headphone.png"
    const val HIDE = "ghosty/Ghosty_hide.png"
    const val HUG = "ghosty/Ghosty_hug.png"
    const val IDLE = "ghosty/Ghosty_idle.png"
    const val LANTERN = "ghosty/Ghosty_lantern.png"
    const val LAUGH = "ghosty/Ghosty_laugh.png"
    const val MEDITATE = "ghosty/Ghosty_meditate.png"
    const val READING = "ghosty/Ghosty_reading.png"
    const val SLEEP = "ghosty/Ghosty_sleep.png"
    const val SMILE = "ghosty/Ghosty_smile.png"
    const val STAR = "ghosty/Ghosty_star.png"
    const val SURPRISE = "ghosty/Ghosty_surprise.png"
    const val THINKING = "ghosty/Ghosty_thinking.png"
    const val ZZZ = "ghosty/Ghosty_zzz.png"
}

/**
 * Estados comportamentais da Ghosty como Buddy oficial do Relaxa e Dorme.
 */
enum class GhostyBuddyStatus {
    WELCOME,
    BROWSING,
    PLAYING,
    PAUSED,
    TIMER_ACTIVE,
    FALLING_ASLEEP,
    SLEEPING,
    WAKING_UP
}

/**
 * Foco sonoro atual quando a Ghosty está no estado [GhostyBuddyStatus.PLAYING].
 */
enum class GhostyPlayingFocus {
    NONE,
    MUSIC,
    AMBIENT_MIXER
}

/**
 * Modelo imutável que representa a pose, a mensagem contextual discreta e a semântica da Ghosty.
 */
data class GhostyBuddyState(
    val status: GhostyBuddyStatus,
    val poseAssetPath: String,
    val message: String? = null,
    val contentDescription: String,
    val playingFocus: GhostyPlayingFocus = GhostyPlayingFocus.NONE,
    val timerFadeMultiplier: Float = 1f,
    val timerCountdownText: String? = null
)

/**
 * Resolvedor determinístico do estado da Ghosty Buddy a partir dos estados reais de áudio e timer.
 */
fun resolveGhostyBuddyState(
    playbackState: MusicPlaybackState,
    mixerState: AmbientMixerState,
    sleepTimerState: SleepTimerState,
    hasSleepSessionEnded: Boolean = false,
    hasPendingWakeUp: Boolean = false,
    isBrowsingLibrary: Boolean = false
): GhostyBuddyState = resolveGhostyBuddyState(
    isMusicPlaying = playbackState.isPlaying,
    hasLoadedTrack = playbackState.currentTrack != null,
    isMixerPlaying = mixerState.anyPlaying,
    hasActiveMixerSounds = mixerState.hasActiveSounds,
    isSleepTimerActive = sleepTimerState.isActive,
    isSleepTimerFadingOut = sleepTimerState.isFadingOut,
    sleepTimerRemainingMillis = sleepTimerState.remainingMillis,
    sleepTimerFadeMultiplier = sleepTimerState.fadeMultiplier,
    hasSleepSessionEnded = hasSleepSessionEnded,
    hasPendingWakeUp = hasPendingWakeUp,
    isBrowsingLibrary = isBrowsingLibrary
)

/**
 * Sobrecarga pura para resolução determinística e testes unitários sem dependências de Android.
 */
fun resolveGhostyBuddyState(
    isMusicPlaying: Boolean,
    hasLoadedTrack: Boolean,
    isMixerPlaying: Boolean,
    hasActiveMixerSounds: Boolean,
    isSleepTimerActive: Boolean,
    isSleepTimerFadingOut: Boolean,
    sleepTimerRemainingMillis: Long,
    sleepTimerFadeMultiplier: Float = 1f,
    hasSleepSessionEnded: Boolean = false,
    hasPendingWakeUp: Boolean = false,
    isBrowsingLibrary: Boolean = false
): GhostyBuddyState {
    val isAnyAudioPlaying = isMusicPlaying || isMixerPlaying

    // 1. Fade-out real do timer em andamento -> Adormecendo
    if (isSleepTimerFadingOut) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.FALLING_ASLEEP,
            poseAssetPath = GhostyAssets.ZZZ,
            message = "Shhh... hora de dormir.",
            contentDescription = "Ghosty adormecendo durante o fade-out",
            timerFadeMultiplier = sleepTimerFadeMultiplier.coerceIn(0f, 1f)
        )
    }

    // 2. Timer de sono ativo -> Preparando para dormir
    if (isSleepTimerActive) {
        val formattedCountdown = formatBuddyTimerRemaining(sleepTimerRemainingMillis)
        return GhostyBuddyState(
            status = GhostyBuddyStatus.TIMER_ACTIVE,
            poseAssetPath = GhostyAssets.BLANKET,
            message = "Desligando em $formattedCountdown",
            contentDescription = "Ghosty aconchegada com cobertor aguardando o timer",
            timerFadeMultiplier = sleepTimerFadeMultiplier.coerceIn(0f, 1f),
            timerCountdownText = formattedCountdown
        )
    }

    // 3. Sessão encerrada pelo timer na sessão atual -> Dormindo
    if (hasSleepSessionEnded && !isAnyAudioPlaying) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.SLEEPING,
            poseAssetPath = GhostyAssets.SLEEP,
            message = null,
            contentDescription = "Ghosty dormindo tranquilamente"
        )
    }

    // 4. Retorno ao aplicativo após sessão encerrada pelo timer -> Acordando
    if (hasPendingWakeUp && !isAnyAudioPlaying) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.WAKING_UP,
            poseAssetPath = GhostyAssets.STAR,
            message = "Bom descanso.",
            contentDescription = "Ghosty acordando com expressão acolhedora"
        )
    }

    // 5. Reproduzindo música ou sons ambientes
    if (isMusicPlaying) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.PLAYING,
            poseAssetPath = GhostyAssets.HEADPHONE,
            message = null,
            contentDescription = "Ghosty ouvindo música com fones de ouvido",
            playingFocus = GhostyPlayingFocus.MUSIC
        )
    }

    if (isMixerPlaying) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.PLAYING,
            poseAssetPath = GhostyAssets.MEDITATE,
            message = null,
            contentDescription = "Ghosty relaxando com sons ambientes",
            playingFocus = GhostyPlayingFocus.AMBIENT_MIXER
        )
    }

    // 6. Conteúdo carregado ou camadas no mixer, porém pausado
    if (hasLoadedTrack || hasActiveMixerSounds) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.PAUSED,
            poseAssetPath = GhostyAssets.IDLE,
            message = null,
            contentDescription = "Ghosty em repouso aguardando retomar"
        )
    }

    // 7. Navegando pela biblioteca sem reprodução ativa
    if (isBrowsingLibrary) {
        return GhostyBuddyState(
            status = GhostyBuddyStatus.BROWSING,
            poseAssetPath = GhostyAssets.READING,
            message = null,
            contentDescription = "Ghosty explorando a biblioteca"
        )
    }

    // 8. Boas-vindas na abertura do app sem áudio
    return GhostyBuddyState(
        status = GhostyBuddyStatus.WELCOME,
        poseAssetPath = GhostyAssets.SMILE,
        message = "Que bom ter você aqui.",
        contentDescription = "Ghosty sorrindo em boas-vindas"
    )
}

internal fun formatBuddyTimerRemaining(remainingMillis: Long): String {
    return SleepTimerState.formatCountdownMillis(remainingMillis)
}

/**
 * Componente visual independente da Ghosty Buddy para a tela inicial.
 *
 * - Exibe a ilustração oficial correspondente ao [state] com [Crossfade] suave.
 * - Reage discretamente ao toque com [GhostyAssets.LAUGH] e retorna ao estado atual sem abrir menus.
 * - Interrompe animações infinitas quando [isAnimationRunning] é `false` ou em [GhostyBuddyStatus.SLEEPING].
 */
@Composable
fun GhostyBuddy(
    state: GhostyBuddyState,
    stageSize: Dp,
    isAnimationRunning: Boolean,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var touchReactionTrigger by remember { mutableIntStateOf(0) }
    var isReactingToTouch by remember { mutableStateOf(false) }

    LaunchedEffect(touchReactionTrigger) {
        if (touchReactionTrigger > 0) {
            isReactingToTouch = true
            delay(1_350L)
            isReactingToTouch = false
        }
    }

    val effectivePose = if (isReactingToTouch) GhostyAssets.LAUGH else state.poseAssetPath
    val shouldAnimateInfinitely = isAnimationRunning && state.status != GhostyBuddyStatus.SLEEPING

    val (targetAmplitudeDp, cycleDurationMs, targetRotationDeg) = when (state.status) {
        GhostyBuddyStatus.WELCOME,
        GhostyBuddyStatus.BROWSING,
        GhostyBuddyStatus.WAKING_UP -> Triple(5f, 3600, 0f)
        GhostyBuddyStatus.PLAYING -> Triple(4f, 4000, 1.4f)
        GhostyBuddyStatus.PAUSED,
        GhostyBuddyStatus.TIMER_ACTIVE -> Triple(2.5f, 4800, 0f)
        GhostyBuddyStatus.FALLING_ASLEEP -> Triple(2f * state.timerFadeMultiplier.coerceIn(0.15f, 1f), 5200, 0f)
        GhostyBuddyStatus.SLEEPING -> Triple(0f, 4000, 0f)
    }

    val floatProgress = if (shouldAnimateInfinitely) {
        val infiniteTransition = rememberInfiniteTransition(label = "ghosty_buddy_motion")
        val animated by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleDurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ghosty_buddy_float"
        )
        animated
    } else {
        0f
    }

    val touchScale by animateFloatAsState(
        targetValue = if (isReactingToTouch && isAnimationRunning) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 360f),
        label = "ghosty_buddy_touch_scale"
    )

    val density = LocalDensity.current
    val translationYPx = with(density) { (floatProgress * targetAmplitudeDp).dp.toPx() }
    val rotationDeg = floatProgress * targetRotationDeg

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(stageSize)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        touchReactionTrigger++
                        onTap()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Brilho radial difuso discreto para integrar a Ghosty ao fundo noturno
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val glowRadius = size.minDimension * 0.48f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AppColors.GhostyHaloInner.copy(alpha = 0.26f),
                            AppColors.GhostyHaloOuter.copy(alpha = 0.11f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }

            Crossfade(
                targetState = effectivePose,
                animationSpec = tween(durationMillis = 320),
                label = "ghosty_buddy_pose"
            ) { targetPose ->
                val ghostyBitmap = rememberAssetCoverBitmap(targetPose)
                val localizedContentDescription = if (isReactingToTouch) {
                    stringResource(R.string.ghosty_cd_touch_happy)
                } else {
                    when (state.status) {
                        GhostyBuddyStatus.FALLING_ASLEEP -> stringResource(R.string.ghosty_cd_falling_asleep)
                        GhostyBuddyStatus.TIMER_ACTIVE -> stringResource(R.string.ghosty_cd_timer_active)
                        GhostyBuddyStatus.SLEEPING -> stringResource(R.string.ghosty_cd_sleeping)
                        GhostyBuddyStatus.WAKING_UP -> stringResource(R.string.ghosty_cd_waking_up)
                        GhostyBuddyStatus.PLAYING -> if (state.playingFocus == GhostyPlayingFocus.AMBIENT_MIXER) {
                            stringResource(R.string.ghosty_cd_playing_mixer)
                        } else {
                            stringResource(R.string.ghosty_cd_playing_music)
                        }
                        GhostyBuddyStatus.PAUSED -> stringResource(R.string.ghosty_cd_paused)
                        GhostyBuddyStatus.BROWSING -> stringResource(R.string.ghosty_cd_browsing)
                        GhostyBuddyStatus.WELCOME -> stringResource(R.string.ghosty_cd_welcome)
                    }
                }
                if (ghostyBitmap != null) {
                    Image(
                        bitmap = ghostyBitmap,
                        contentDescription = localizedContentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(stageSize * 0.82f)
                            .graphicsLayer {
                                translationY = translationYPx
                                rotationZ = rotationDeg
                                scaleX = touchScale
                                scaleY = touchScale
                            }
                    )
                } else {
                    AppIcon(
                        iconRes = AppIcons.Bedtime,
                        contentDescription = localizedContentDescription,
                        tint = AppColors.GhostyMoonCream.copy(alpha = 0.88f),
                        modifier = Modifier
                            .size(stageSize * 0.42f)
                            .graphicsLayer {
                                translationY = translationYPx
                                rotationZ = rotationDeg
                                scaleX = touchScale
                                scaleY = touchScale
                            }
                    )
                }
            }
        }

        val localizedMessage = when (state.status) {
            GhostyBuddyStatus.FALLING_ASLEEP -> stringResource(R.string.ghosty_msg_falling_asleep)
            GhostyBuddyStatus.TIMER_ACTIVE -> state.timerCountdownText?.let {
                stringResource(R.string.ghosty_msg_timer_active, it)
            } ?: state.message
            GhostyBuddyStatus.WAKING_UP -> stringResource(R.string.ghosty_msg_waking_up)
            GhostyBuddyStatus.BROWSING -> stringResource(R.string.ghosty_msg_browsing)
            GhostyBuddyStatus.WELCOME -> stringResource(R.string.ghosty_msg_welcome)
            GhostyBuddyStatus.SLEEPING,
            GhostyBuddyStatus.PLAYING,
            GhostyBuddyStatus.PAUSED -> null
        }

        // Mensagem contextual discreta e reservada apenas quando houver significado real
        Box(
            modifier = Modifier
                .height(22.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = localizedMessage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "ghosty_buddy_message"
            ) { msg ->
                if (!msg.isNullOrBlank()) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
